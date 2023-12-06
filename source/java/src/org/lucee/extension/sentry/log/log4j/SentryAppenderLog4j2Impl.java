package org.lucee.extension.sentry.log.log4j;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.lucee.extension.sentry.log.util.CommonUtil;

import io.sentry.DateUtils;
import io.sentry.IHub;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import lucee.Info;
import lucee.commons.io.log.Log;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.PageSource;
import lucee.runtime.config.Config;
import lucee.runtime.exp.CatchBlock;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.type.Array;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;

public class SentryAppenderLog4j2Impl extends AbstractAppender {

	private static final Class[] EMPTY_CLASS = new Class[0];
	private static final Object[] EMPTY_OBJ = new Object[0];
	private static BIF contractPath;
	private static Method getRequestId;

	private Config config;
	private final String dsn;
	private final boolean debug;
	private IHub hub;

	private String name;

	private String dist;

	private String environment;

	private Map<String, String> extras;

	private Map<String, String> tags;

	private static final Object token = new Object();

	public SentryAppenderLog4j2Impl(String name, Filter filter, Layout<? extends Serializable> layout, String dsn,
			boolean debug, String dist, String environment, Map<String, String> extras, Map<String, String> tags) {
		super(name, filter, layout, false);
		this.dsn = dsn;
		this.name = name;
		this.debug = debug;
		this.dist = dist;
		this.environment = environment;
		this.extras = extras != null && extras.isEmpty() ? null : extras;
		this.tags = tags != null && tags.isEmpty() ? null : tags;

		try {
			config = CFMLEngineFactory.getInstance().getThreadConfig();
		} catch (Exception e) {
		}
	}

	private static SentryEvent ev() {
		try {
			throw new Exception("this is just a test of the sentry exception, please ignore!!!! " + new Date());
		} catch (Exception e) {

			SentryEvent event = new SentryEvent();
			Message message = new Message();
			message.setMessage("Exception caught");
			event.setMessage(message);
			event.setLevel(SentryLevel.INFO);
			event.setLogger("scope");
			event.setThrowable(e);
			return event;
		}
	}

	private void init() {
		if (hub == null) {
			synchronized (token) {
				if (hub == null) {
					SentryOptions so = new SentryOptions();
					so.setDsn(dsn);
					so.setDebug(debug);
					so.setEnableExternalConfiguration(true);
					so.setSentryClientName("lucee.sentry.log4j2");
					Sentry.init(so); // Micha: i make it this way, because a direct instance of Hub does not work
					hub = Sentry.getCurrentHub();
				}
			}
		}
	}

	@Override
	public void append(LogEvent event) {
		if (!Util.isEmpty(dsn)) {
			init();

			if (hub.isEnabled()) {
				try {
					hub.captureEvent(createEvent(event));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			Throwable t = event.getThrown();
			CFMLEngineFactory.getInstance().getThreadConfig().getLog("application").log(toLevel(event.getLevel()), name,
					event.getMessage().toString(), t);
		}
	}

	private static int toLevel(Level level) {
		if (level.equals(Level.DEBUG))
			return Log.LEVEL_DEBUG;
		if (level.equals(Level.ERROR))
			return Log.LEVEL_ERROR;
		if (level.equals(Level.FATAL))
			return Log.LEVEL_FATAL;
		if (level.equals(Level.INFO))
			return Log.LEVEL_INFO;
		if (level.equals(Level.TRACE))
			return Log.LEVEL_TRACE;
		if (level.equals(Level.WARN))
			return Log.LEVEL_WARN;
		return 0;
	}

	protected SentryEvent createEvent(final LogEvent loggingEvent) {
		final SentryEvent event = new SentryEvent(DateUtils.getDateTime(loggingEvent.getTimeMillis()));
		final Message message = new Message();

		String application;
		String[] data = SentryUtil.appAndMsg(loggingEvent.getMessage().getFormattedMessage());
		message.setMessage(loggingEvent.getMessage().getFormat());
		message.setFormatted(data[1]);
		message.setParams(toParams(loggingEvent.getMessage().getParameters()));
		event.setMessage(message);
		event.setLogger(loggingEvent.getLoggerName());
		event.setLevel(formatLevel(loggingEvent.getLevel()));
		event.setExtra("Application", data[0]);
		PageContext pc = CFMLEngineFactory.getInstance().getThreadPageContext();
		if (pc != null) {
			PageSource ps = pc.getCurrentPageSource();
			if (ps != null)
				event.setExtra("Current Template", ps.getDisplayPath());
			ps = pc.getBasePageSource();
			if (ps != null)
				event.setExtra("Base Template", ps.getDisplayPath());

			if (pc.getRequest() instanceof HttpServletRequest) {

				HttpServletRequest req = (HttpServletRequest) pc.getRequest();
				String url = SentryUtil.getRequestURL(req, true);
				event.setExtra("Request URL", url);
				event.setTag("url", url);
				event.setTag("server_name", req.getServerName());

			}
			String caller = getCaller(pc);
			if (caller != null)
				event.setExtra("Caller", caller);

			event.setTag("request_id", getRequestId(pc) + "");
			event.setTag("id", pc.getId() + "");

		}

		Info info = CFMLEngineFactory.getInstance().getInfo();

		// tags
		event.setTag("lucee_version", info.getVersion().toString());
		event.setTag("java_version", System.getProperty("java.version"));
		event.setTag("machine_name", System.getenv("MACHINE_NAME"));
		event.setTag("machine_name", System.getenv("MACHINE_PURPOSE"));

		setDD(event, "APP", "application");
		setDD(event, "ENV", "environment");
		setDD(event, "PURPOSE", "purpose");
		setDD(event, "SERVICE", "service");
		setDD(event, "TAGS", "tags");
		setDD(event, "VERSION", "version");
		setDD(event, "TAGS", "tags");

		event.setExtra("Lucee Core", info.getVersion().toString());
		event.setExtra("Lucee Loader", lucee.loader.Version.VERSION);

		// branch,commit,release,dev_user,

		Throwable t = loggingEvent.getThrown();
		if (t != null) {
			event.setThrowable(t);
		}

		if (loggingEvent.getThreadName() != null) {
			event.setExtra("Thread Name", loggingEvent.getThreadName());
		}

		if (loggingEvent.getMarker() != null) {
			event.setExtra("Marker", loggingEvent.getMarker().toString());
		}

		Map<String, String> contextData = loggingEvent.getContextData().toMap();
		Map<String, String> trg = new HashMap<>();
		if (!contextData.isEmpty()) {
			Iterator<Entry<String, String>> it = contextData.entrySet().iterator();
			while (it.hasNext()) {
				Entry<String, String> e = it.next();
				if (e.getValue() != null)
					trg.put(e.getKey(), e.getValue());
			}
		}
		if (!trg.isEmpty()) {
			event.getContexts().put("Context Data", trg);
		}

		CommonUtil.setCustom(event, dist, environment, tags, extras);

		return event;
	}

	private void setDD(SentryEvent event, String envName, String tagName) {
		String tmp = System.getenv(envName);
		if (Util.isEmpty(tmp, true)) {
			tmp = System.getenv("DD_" + envName);
		}
		if (!Util.isEmpty(tmp, true)) {
			event.setTag(tagName, tmp);
		}
	}

	public static String getRequestId(PageContext pc) {
		try {
			if (getRequestId == null || getRequestId.getDeclaringClass() != pc.getClass())
				getRequestId = pc.getClass().getMethod("getRequestId", EMPTY_CLASS);
			return CFMLEngineFactory.getInstance().getCastUtil().toString(getRequestId.invoke(pc, EMPTY_OBJ));
		} catch (Exception e) {
			if (pc != null) {
				Log log = pc.getConfig().getLog("application");
				if (log != null)
					log.error("sentry appener", e);
			}

			return null;
		}
	}

	private List<String> toParams(final Object[] arguments) {
		if (arguments == null || arguments.length == 0)
			return Collections.emptyList();

		List<String> list = new ArrayList<>();
		for (Object o : arguments) {
			if (o != null)
				list.add(o.toString());
		}
		return list;
	}

	private static SentryLevel formatLevel(final Level level) {
		if (level.isMoreSpecificThan(Level.FATAL)) {
			return SentryLevel.FATAL;
		} else if (level.isMoreSpecificThan(Level.ERROR)) {
			return SentryLevel.ERROR;
		} else if (level.isMoreSpecificThan(Level.WARN)) {
			return SentryLevel.WARNING;
		} else if (level.isMoreSpecificThan(Level.INFO)) {
			return SentryLevel.INFO;
		} else {
			return SentryLevel.DEBUG;
		}
	}

	private String getCaller(PageContext pc) {
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		Cast caster = engine.getCastUtil();

		PageException pe = caster.toPageException(new Throwable());
		CatchBlock cb = pe.getCatchBlock(getConfig(pc));
		Array arr = caster.toArray(cb.get("TagContext", null), null);

		// tag context
		if (arr.size() > 0) {
			Iterator<?> it = arr.getIterator();
			Struct sct;
			String template, filename;
			int line;
			while (it.hasNext()) {
				sct = caster.toStruct(it.next(), null);
				if (sct == null)
					continue;
				template = caster.toString(sct.get("template", ""), "");
				filename = null;
				if (pc != null) {
					try {
						filename = contractPath(pc, template);
					} catch (PageException e) {
					}
				}
				if (filename == null)
					filename = engine.getListUtil().last(template, "\\/", true);

				line = caster.toIntValue(sct.get("line", 0), 0);
				return filename + ":" + line;
			}
		}
		return null;
	}

	private Config getConfig(PageContext pc) {
		if (pc != null) {
			if (this.config == null)
				config = pc.getConfig();
			return pc.getConfig();
		}
		if (this.config == null)
			this.config = CFMLEngineFactory.getInstance().getThreadConfig();

		return this.config;
	}

	private static String contractPath(PageContext pc, String abs) throws PageException {
		try {
			if (contractPath == null || contractPath.getClass() == CFMLEngineFactory.getInstance().getClass())
				contractPath = CFMLEngineFactory.getInstance().getClassUtil().loadBIF(pc,
						"lucee.runtime.functions.system.ContractPath");
			return (String) contractPath.invoke(pc, new Object[] { abs });
		} catch (Exception e) {
			throw CFMLEngineFactory.getInstance().getCastUtil().toPageException(e);
		}
	}

}
