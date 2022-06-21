package org.lucee.extension.sentry.log.log4j;

import java.io.IOException;
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
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import io.sentry.DateUtils;
import io.sentry.IHub;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import lucee.Info;
import lucee.commons.io.res.Resource;
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

	private static BIF contractPath;

	private Config config;
	// private String strTimeout;
	// private Integer timeout;
	private final Level threshold;
	private final String dsn;
	private final String logger;
	private final boolean debug;

	private Appender localAppender;
	private IHub hub;

	private Layout<? extends Serializable> layout;

	private String name;
	private String path;
	private String charset;
	private String maxfiles;
	private String maxfileSize;
	private String timeout;

	private static final Object token = new Object();

	public SentryAppenderLog4j2Impl(String name, Filter filter, Layout<? extends Serializable> layout, String dsn,
			Level threshold, String logger, boolean debug, String path, String charset, String maxfiles,
			String maxfileSize, String timeout) {
		super(name, filter, layout, false);
		this.name = name;
		this.dsn = dsn;
		this.threshold = threshold;
		this.layout = layout;
		this.logger = logger;
		this.debug = debug;
		this.path = path;
		this.charset = charset;
		this.maxfiles = maxfiles;
		this.maxfileSize = maxfileSize;
		this.timeout = timeout;
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
		init();
		boolean done = false;

		if (hub.isEnabled() && event.getLevel().isMoreSpecificThan(threshold)) {
			try {
				hub.captureEvent(createEvent(event));
				done = true;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (!done && false) {
			try {
				local().append(event);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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

			if (pc.getRequest() instanceof HttpServletRequest)
				event.setExtra("Request URL", SentryUtil.getRequestURL((HttpServletRequest) pc.getRequest(), true));

			String caller = getCaller(pc);
			if (caller != null)
				event.setExtra("Caller", caller);

		}

		Info info = CFMLEngineFactory.getInstance().getInfo();
		event.setExtra("Lucee Core", info.getVersion().toString());
		event.setExtra("Lucee Loader", lucee.loader.Version.VERSION);

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

		return event;
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

	/*
	 * private int getTimeout() { if (timeout != null) return timeout.intValue();
	 * return timeout =
	 * CFMLEngineFactory.getInstance().getCastUtil().toInteger(strTimeout, 60); }
	 */

	private Appender local() throws PageException {
		if (localAppender == null) {
			Map<String, String> appenderArgs = new HashMap<>();
			appenderArgs.put("path", getPath());
			appenderArgs.put("charset", charset);
			appenderArgs.put("maxfiles", maxfiles);
			appenderArgs.put("maxfilesize", maxfileSize);
			appenderArgs.put("timeout", timeout);

			localAppender = Log4j2Engine.getAppender(config, layout, name, "resource", appenderArgs);
		}
		return localAppender;
	}

	private String getPath() {
		CFMLEngine engine = CFMLEngineFactory.getInstance();

		// name
		String name = this.name;
		if (Util.isEmpty(name)) {
			name = hashCode() + "";
		}

		// default path
		if (Util.isEmpty(this.path))
			this.path = "logs/" + name + ".log";
		else
			this.path = this.path.trim();

		Config config = getConfig(null);
		Resource path = getFile(engine, config, config.getConfigDir(), this.path);
		if (path.isDirectory())
			path = path.getRealResource(name + ".log");

		System.err.println("path:" + path);

		return path.getAbsolutePath();
	}

	private static Resource getFile(CFMLEngine engine, Config config, Resource directory, String path) {
		if (Util.isEmpty(path, true))
			return null;

		path = replacePlaceholder(path, config);
		Resource file = config.getResource(path);
		if (isDirectoryP(file)) {
			if (!file.isFile()) {
				try {
					file.createFile(true);
				} catch (IOException e) {
				}
			}
			return file;
		}

		file = directory.getRealResource(path);
		if (isDirectoryP(file) || isDirectoryPP(file)) {
			try {
				if (!file.isFile())
					file.createFile(true);
				return file;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private static boolean isDirectoryP(Resource file) {
		if (file == null)
			return false;
		Resource p = file.getParentResource();
		return p != null && p.isDirectory();
	}

	private static boolean isDirectoryPP(Resource file) {
		if (file == null)
			return false;
		Resource p = file.getParentResource();

		if (p == null || !p.isDirectory())
			return false;

		p = p.getParentResource();
		return p != null && p.isDirectory();
	}

	private static String replacePlaceholder(String path, Config config) {
		try {
			Class<?> clazz = CFMLEngineFactory.getInstance().getClassUtil()
					.loadClass("lucee.runtime.config.ConfigWebUtil");
			Method m = clazz.getMethod("replacePlaceholder", new Class[] { String.class, Config.class });
			return (String) m.invoke(null, new Object[] { path, config });
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
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