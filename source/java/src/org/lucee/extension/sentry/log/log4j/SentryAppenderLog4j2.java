package org.lucee.extension.sentry.log.log4j;

import java.io.Serializable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.ErrorHandler;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;

import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;

public class SentryAppenderLog4j2 implements Appender {

	private Layout layout;
	private Filter filter;
	private String name;
	private String logger = "application";
	private String strTimeout;
	private String dsn;
	private Level threshold = Level.ERROR;
	private SentryAppenderLog4j2Impl instance;
	private boolean debug = true;
	private String strPath;
	private String strCharset;
	private String strMaxfiles;
	private String strMaxfileSize;
	private static final Object token = new Object();

	public SentryAppenderLog4j2() {

	}

	private SentryAppenderLog4j2Impl inst() {
		if (instance == null) {
			synchronized (token) {
				if (instance == null) {
					instance = new SentryAppenderLog4j2Impl(name, filter, layout, dsn, threshold, logger, debug,
							strPath, strCharset, strMaxfiles, strMaxfileSize, strTimeout);
					instance.start();
				}
			}
		}
		return instance;
	}

	public void setDsn(String dsn) {
		this.dsn = dsn;
	}

	public void setDebug(String debug) {
		this.debug = CFMLEngineFactory.getInstance().getCastUtil().toBooleanValue(debug, true);
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

	public void setThreshold(Level threshold) {
		this.threshold = threshold;
	}

	public void setThreshold(String threshold) {
		this.threshold = toLevel(threshold);
		// sentry().setThreshold(toPriority(threshold));
	}

	public void setResourceTimeout(String timeout) {
		this.strTimeout = timeout;
	}

	public void setResourcepath(String path) {
		this.strPath = path;
	}

	public void setResourcecharset(String charset) {
		this.strCharset = charset;
	}

	public void setResourcemaxfiles(String maxfiles) {
		this.strMaxfiles = maxfiles;
	}

	public void setResourcemaxfilesize(String maxFileSize) {
		this.strMaxfileSize = maxFileSize;
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