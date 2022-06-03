package org.lucee.extension.sentry.log.log4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.impl.ThrowableProxy;

import io.sentry.DateUtils;
import io.sentry.Hub;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import lucee.commons.io.log.Log;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.config.Config;
import lucee.runtime.exp.CatchBlock;
import lucee.runtime.exp.PageException;
import lucee.runtime.ext.function.BIF;
import lucee.runtime.type.Array;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;

public class SentryAppender extends AbstractAppender {

	private static BIF contractPath;
	private static final Object token = new Object();

	private String strTimeout;
	private Integer timeout;
	private Layout layout;
	private String name;
	private Level threshold = Level.ERROR;

	private Config config;
	private String dsn;
	// private HubAdapter hub;
	private boolean debug = true;
	private String logger = "application";

	private Log local;
	private boolean isInit;
	private Hub hub;

	public SentryAppender() {
		this(null, null, null, null);
	}

	public SentryAppender(String dsn) {
		this(null, null, null, dsn);
		this.dsn = dsn;

	}

	protected SentryAppender(String name, Filter filter, Layout<? extends Serializable> layout, String dsn) {
		super(name, filter, layout);

		try {
			config = CFMLEngineFactory.getInstance().getThreadConfig();
		} catch (Exception e) {
		}
		// hub = HubAdapter.getInstance();

	}

	private void init() {
		if (!isInit) {
			synchronized (token) {
				if (!isInit) {

					SentryOptions so = new SentryOptions();
					so.setDsn(dsn);
					so.setDebug(debug);
					so.setEnableExternalConfiguration(true);
					so.setSentryClientName(io.sentry.log4j2.BuildConfig.SENTRY_LOG4J2_SDK_NAME);
					hub = new Hub(so);

					isInit = true;
				}
			}
		}
	}

	public void setDsn(String dsn) {
		this.dsn = dsn;
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
			}
		}
		if (!done) {
			Log l = local();
			if (l != null) {

				final ThrowableProxy throwableInformation = event.getThrownProxy();
				String msg = event.getMessage().getFormattedMessage();
				if (throwableInformation != null) {
					l.log(toLevel(event.getLevel(), Log.LEVEL_ERROR), msg, throwableInformation.getThrowable());

				} else {
					l.log(toLevel(event.getLevel(), Log.LEVEL_ERROR), msg, "");
				}
			}
		}
	}

	protected SentryEvent createEvent(final LogEvent loggingEvent) {
		final SentryEvent event = new SentryEvent(DateUtils.getDateTime(loggingEvent.getTimeMillis()));
		final Message message = new Message();
		message.setMessage(loggingEvent.getMessage().getFormat());
		message.setFormatted(loggingEvent.getMessage().getFormattedMessage());
		message.setParams(toParams(loggingEvent.getMessage().getParameters()));
		event.setMessage(message);
		event.setLogger(loggingEvent.getLoggerName());
		event.setLevel(formatLevel(loggingEvent.getLevel()));

		final ThrowableProxy throwableInformation = loggingEvent.getThrownProxy();
		if (throwableInformation != null) {
			event.setThrowable(throwableInformation.getThrowable());
		}

		if (loggingEvent.getThreadName() != null) {
			event.setExtra("thread_name", loggingEvent.getThreadName());
		}

		if (loggingEvent.getMarker() != null) {
			event.setExtra("marker", loggingEvent.getMarker().toString());
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

		// caller
		PageContext pc = CFMLEngineFactory.getInstance().getThreadPageContext();
		if (pc != null) {
			String caller = getCaller(pc);
			if (caller != null)
				event.setExtra("caller", caller);
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

	public void setThreshold(Level threshold) {
		this.threshold = threshold;
	}

	public void setThreshold(String threshold) {
		this.threshold = toLevel(threshold);
		// sentry().setThreshold(toPriority(threshold));
	}

	public void setLogger(String logger) {
		if (!Util.isEmpty(logger, true))
			this.logger = logger.trim();
	}

	public void setResourceTimeout(String timeout) {
		this.strTimeout = timeout;
	}

	private int getTimeout() {
		if (timeout != null)
			return timeout.intValue();
		return timeout = CFMLEngineFactory.getInstance().getCastUtil().toInteger(strTimeout, 60);
	}

	private Log local() {
		if (local == null) {

			if (config == null)
				config = CFMLEngineFactory.getInstance().getThreadConfig();

			try {
				local = config.getLog(logger);
			} catch (Exception e) {
				local = config.getLog("application");
			}

		}
		return local;
	}

	@Override
	public Layout getLayout() {
		return layout;
	}

	public void setLayout(Layout layout) {
		this.layout = layout;
	}

	public void setFilter(Filter f) {
		super.addFilter(f);
	}

	@Override
	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		if (!Util.isEmpty(name, true))
			this.name = name.trim();
		// local().setName(name);
		// sentry().setName(name);
	}

	private Level toLevel(String level) {
		if (Util.isEmpty(level, true))
			return null;
		level = level.trim();

		if ("debug".equalsIgnoreCase(level))
			return Level.DEBUG;
		if ("error".equalsIgnoreCase(level))
			return Level.ERROR;
		if ("fatal".equalsIgnoreCase(level))
			return Level.FATAL;
		if ("info".equalsIgnoreCase(level))
			return Level.INFO;
		if ("trace".equalsIgnoreCase(level))
			return Level.TRACE;
		if ("warn".equalsIgnoreCase(level))
			return Level.WARN;

		return null;
	}

	private int toLevel(Level l, int dv) {
		if (Level.DEBUG.equals(l))
			return Log.LEVEL_DEBUG;
		if (Level.ERROR.equals(l))
			return Log.LEVEL_ERROR;
		if (Level.INFO.equals(l))
			return Log.LEVEL_INFO;
		if (Level.FATAL.equals(l))
			return Log.LEVEL_FATAL;
		if (Level.TRACE.equals(l))
			return Log.LEVEL_TRACE;
		if (Level.WARN.equals(l))
			return Log.LEVEL_WARN;
		return dv;
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
