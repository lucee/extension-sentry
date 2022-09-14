package org.lucee.extension.sentry.log.log4j;

import java.io.Serializable;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.ErrorHandler;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.lucee.extension.sentry.log.util.CommonUtil;

import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;

public class SentryAppenderLog4j2 implements Appender {

	private Layout layout;
	private Filter filter;
	private String name;
	private String dsn;
	private SentryAppenderLog4j2Impl instance;
	private boolean debug = false;
	private String environment;
	private String dist;
	private Map<String, String> extras;
	private Map<String, String> tags;
	private static final Object token = new Object();

	public SentryAppenderLog4j2() {

	}

	private SentryAppenderLog4j2Impl inst() {
		if (instance == null) {
			synchronized (token) {
				if (instance == null) {
					instance = new SentryAppenderLog4j2Impl(name, filter, layout, dsn, debug, dist, environment, extras,
							tags);
					instance.start();
				}
			}
		}
		return instance;
	}

	public void setDsn(String dsn) {
		this.dsn = dsn;
	}

	public void setEnvironment(String environment) {
		if (!Util.isEmpty(environment, true))
			this.environment = environment.trim();
	}

	public void setDist(String dist) {
		if (!Util.isEmpty(dist, true))
			this.dist = dist.trim();
	}

	public void setExtras(String extras) {
		if (Util.isEmpty(dist, true))
			return;
		this.extras = CommonUtil.toMap(extras);
	}

	public void setTags(String tags) {
		if (Util.isEmpty(dist, true))
			return;
		this.tags = CommonUtil.toMap(tags);
	}

	public void setDebug(String debug) {
		if (Util.isEmpty(debug)) {
			this.debug = false;
		} else {
			this.debug = CFMLEngineFactory.getInstance().getCastUtil().toBooleanValue(debug, true);
		}
	}

	public void setLayout(Layout layout) {
		this.layout = layout;
	}

	public void setFilter(Filter filter) {
		this.filter = filter;
	}

	public void setName(String name) {
		if (!Util.isEmpty(name, true))
			this.name = name.trim();
	}

	@Override
	public State getState() {
		return inst().getState();
	}

	@Override
	public void initialize() {
		inst().initialize();
	}

	@Override
	public boolean isStarted() {
		return inst().isStarted();
	}

	@Override
	public boolean isStopped() {
		return inst().isStopped();
	}

	@Override
	public void start() {
		if (!inst().isStarted())
			inst().start();
	}

	@Override
	public void stop() {
		if (instance != null && isStarted()) {
			inst().stop();
			instance = null;
		}
	}

	@Override
	public void append(LogEvent event) {
		inst().append(event);
	}

	@Override
	public ErrorHandler getHandler() {
		return inst().getHandler();
	}

	@Override
	public Layout<? extends Serializable> getLayout() {
		return inst().getLayout();
	}

	@Override
	public String getName() {
		return inst().getName();
	}

	@Override
	public boolean ignoreExceptions() {
		return inst().ignoreExceptions();
	}

	@Override
	public void setHandler(ErrorHandler handler) {
		inst().setHandler(handler);
	}

	private static Level toLevel(String level) {
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
}
