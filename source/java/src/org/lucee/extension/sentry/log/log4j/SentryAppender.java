package org.lucee.extension.sentry.log.log4j;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.Charset;

import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

import io.sentry.Sentry;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import lucee.commons.io.res.Resource;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.PageContext;
import lucee.runtime.PageSource;
import lucee.runtime.config.Config;
import lucee.runtime.exp.PageException;

public class SentryAppender implements Appender {

	private Appender local = null;
	private io.sentry.log4j.SentryAppender sentry = null;
	private Resource path;
	private String strPath;
	private String strCharset;
	private Charset charset;
	private String strMaxfiles;
	private Integer maxfiles = null;
	private String strMaxfileSize;
	private Long maxfileSize;
	private String strTimeout;
	private Integer timeout;
	private Layout layout;
	private String name;
	private Priority threshold = Priority.ERROR;

	public SentryAppender() {

	}

	public SentryAppender(String dsn) {
		Sentry.init(dsn);
	}

	public void setDsn(String dsn) {
		Sentry.init(dsn);

	}

	private io.sentry.log4j.SentryAppender sentryX() {
		if (sentry == null) {
			sentry = new io.sentry.log4j.SentryAppender();
			sentry.setThreshold(Priority.ERROR);
			if (!Util.isEmpty(name))
				sentry.setName(name);
			if (layout != null)
				sentry.setLayout(layout);

		}
		return sentry;
	}

	public Event toEvent(LoggingEvent le) {
		EventBuilder eb = new EventBuilder();
		eb.withMessage(le.getMessage().toString()).withLevel(toLevel(le.getLevel())).withLogger(le.getLoggerName());

		PageContext pc = CFMLEngineFactory.getInstance().getThreadPageContext();
		StackTraceElement[] traces = new Throwable().getStackTrace();
		boolean hasTransaction = false;
		if (pc != null) {
			StackTraceElement ste = getContext(pc, traces);
			if (ste != null) {
				String str = toString(ste);
				eb.withFingerprint(le.getLoggerName() + ":" + str);
				eb.withTransaction(str);
				hasTransaction = true;
			}

			// current template path
			PageSource curr = pc.getCurrentTemplatePageSource();
			if (curr != null) {
				try {
					String _path = curr.getResourceTranslated(pc).getAbsolutePath();
					if (_path != null) {
						eb.withExtra("CurrentTemplatePath", _path);

					}
				} catch (PageException e) {
				}
			}

			// base template path
			PageSource base = pc.getBasePageSource();
			if (base != null) {
				try {
					String _path = base.getResourceTranslated(pc).getAbsolutePath();
					if (_path != null) {
						eb.withExtra("BaseTemplatePath", _path);

					}
				} catch (PageException e) {
				}
			}

		}

		String firstSTE = toString(traces);
		if (!hasTransaction) {
			eb.withFingerprint(le.getLoggerName() + ":" + firstSTE);
			eb.withTransaction(firstSTE);
		}
		eb.withExtra("Caller", firstSTE);
		eb.withExtra("LogName", le.getLoggerName().substring(le.getLoggerName().lastIndexOf('.') + 1));
		eb.withExtra("LogType", le.getLoggerName().substring(0, le.getLoggerName().indexOf('.')));
		eb.withExtra("LogLevel", (le.getLevel().toString()));

		return eb.build();
	}

	private static StackTraceElement getContext(PageContext pc, StackTraceElement[] traces) {
		String template;
		for (StackTraceElement ste : traces) {
			template = ste.getFileName();
			if (ste.getLineNumber() <= 0 || template == null || template.endsWith(".java"))
				continue;
			return ste;
		}
		return null;
	}

	private String toString(StackTraceElement ste) {
		StringBuilder sb = new StringBuilder();

		sb.append(ste.getClassName()).append(".").append(ste.getMethodName());
		String fileName = ste.getFileName();
		if (fileName != null && !fileName.isEmpty()) {
			sb.append("(").append(fileName);
			if (ste.getLineNumber() >= 0) {
				sb.append(":").append(ste.getLineNumber());
			}
			sb.append(")");
		}

		return sb.toString();
	}

	private String toString(StackTraceElement[] stes) {
		boolean after = false;

		for (StackTraceElement ste : stes) {
			if (after) {
				if (ste.getClassName().equals("lucee.runtime.tag.Log") && ste.getMethodName().equals("doStartTag"))
					continue;
				return toString(ste);
			}
			if (ste.getClassName().equals("lucee.commons.io.log.log4j.LogAdapter")
					&& ste.getMethodName().equals("log")) {
				after = true;
				continue;
			}

			if (ste.getClassName().startsWith("lucee.commons.io.log."))
				continue;
			if (ste.getClassName().startsWith("org.lucee.extension.sentry.log.log4j."))
				continue;
			if (ste.getClassName().equals("lucee.runtime.tag.Log"))
				continue;

			return toString(ste);
		}
		return null;
	}

	public void setThreshold(Priority threshold) {
		this.threshold = threshold;
		// sentry().setThreshold(threshold);
	}

	public void setThreshold(String threshold) {
		this.threshold = toPriority(threshold);
		// sentry().setThreshold(toPriority(threshold));
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

	public void setResourceTimeout(String timeout) {
		this.strTimeout = timeout;
	}

	private Charset getCharset() {
		if (charset != null)
			return charset;

		if (Util.isEmpty(strCharset))
			strCharset = "UTF-8";
		return charset = CFMLEngineFactory.getInstance().getCastUtil().toCharset(strCharset, null);
	}

	private int getMaxfiles() {
		if (maxfiles != null)
			return maxfiles.intValue();
		return maxfiles = CFMLEngineFactory.getInstance().getCastUtil().toInteger(strMaxfiles, 10);
	}

	private long getMaxfileSize() {
		if (maxfileSize != null)
			return maxfileSize.longValue();
		return maxfileSize = CFMLEngineFactory.getInstance().getCastUtil().toLong(strMaxfileSize,
				(long) (1024 * 1024 * 10));
	}

	private int getTimeout() {
		if (timeout != null)
			return timeout.intValue();
		return timeout = CFMLEngineFactory.getInstance().getCastUtil().toInteger(strTimeout, 60);
	}

	private Resource getPath() {
		if (path != null)
			return path;

		CFMLEngine engine = CFMLEngineFactory.getInstance();
		// TODO config == null
		Config config = engine.getThreadConfig();

		// name
		String name = this.name;
		if (Util.isEmpty(name)) {
			name = hashCode() + "";
		}

		// default path
		if (Util.isEmpty(strPath))
			strPath = "logs/" + name + ".log";
		else
			strPath = strPath.trim();

		path = getFile(engine, config, config.getConfigDir(), strPath);
		if (path.isDirectory())
			path = path.getRealResource(name + ".log");
		return path;
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

	private Appender local() {
		// <logger appender="resource"
		// appender-arguments="path:{lucee-config}/logs/scope.log" layout="classic"
		// name="scope"/>
		if (local == null) {
			try {
				CFMLEngine engine = CFMLEngineFactory.getInstance();
				// Config config = engine.getThreadConfig();

				Class<?> clazzRL = engine.getClassUtil().loadClass("lucee.commons.io.retirement.RetireListener");

				Class<?> clazzRRA = engine.getClassUtil()
						.loadClass("lucee.commons.io.log.log4j.appender.RollingResourceAppender");

				if (this.layout == null) {
					Class<?> clazz = engine.getClassUtil().loadClass("lucee.commons.io.log.log4j.layout.ClassicLayout");
					this.layout = (Layout) clazz.newInstance();
				}

				Constructor<?> c = clazzRRA.getConstructor(new Class[] { Layout.class, Resource.class, Charset.class,
						boolean.class, long.class, int.class, int.class, clazzRL });
				Appender a = (Appender) c.newInstance(layout, getPath(), getCharset(), true, getMaxfileSize(),
						getMaxfiles(), getTimeout(), null);
				if (!Util.isEmpty(name, true))
					a.setName(name.trim());
				if (layout != null)
					a.setLayout(layout);
				return a;

			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			// appender = new RollingResourceAppender(toLayout(layout), res, charset, true,
			// maxfilesize, maxfiles, timeout, null);
			// TODO
		}
		return local;
	}

	@Override
	public void addFilter(Filter filter) {
		local().addFilter(filter);
		// sentry().addFilter(filter);
	}

	@Override
	public void clearFilters() {
		local().clearFilters();
		// sentry().clearFilters();
	}

	@Override
	public Filter getFilter() {
		return local().getFilter();
	}

	@Override
	public void close() {
		local().close();
		// sentry().close();
	}

	@Override
	public void doAppend(LoggingEvent le) {

		le.getLocationInformation().fullInfo = le.getLocationInformation().fullInfo + "qqq";
		if (le.getLevel().isGreaterOrEqual(threshold)) {
			// sentry().doAppend(toProperLI(le));
			Sentry.capture(toEvent(le));
		} else
			local().doAppend(le);
	}

	@Override
	public ErrorHandler getErrorHandler() {
		return local().getErrorHandler();
	}

	@Override
	public Layout getLayout() {
		return layout;
	}

	@Override
	public void setLayout(Layout layout) {
		this.layout = layout;
		// local().setLayout(layout);
		// sentry().setLayout(layout);
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public void setName(String name) {
		if (!Util.isEmpty(name, true))
			this.name = name.trim();
		// local().setName(name);
		// sentry().setName(name);
	}

	@Override
	public boolean requiresLayout() {
		return local().requiresLayout() || local().requiresLayout();
	}

	@Override
	public void setErrorHandler(ErrorHandler eh) {
		local().setErrorHandler(eh);
		// sentry().setErrorHandler(eh);
	}

	private int toLevel(Priority p) {
		if (Priority.DEBUG_INT == p.toInt())
			return Level.DEBUG_INT;
		if (Priority.ERROR_INT == p.toInt())
			return Level.ERROR_INT;
		if (Priority.FATAL_INT == p.toInt())
			return Level.FATAL_INT;
		if (Priority.INFO_INT == p.toInt())
			return Level.INFO_INT;
		if (Priority.WARN_INT == p.toInt())
			return Level.WARN_INT;
		return Level.ERROR_INT;
	}

	private io.sentry.event.Event.Level toLevel(Level p) {
		if (Priority.DEBUG_INT == p.toInt())
			return io.sentry.event.Event.Level.DEBUG;
		if (Priority.ERROR_INT == p.toInt())
			return io.sentry.event.Event.Level.ERROR;
		if (Priority.FATAL_INT == p.toInt())
			return io.sentry.event.Event.Level.FATAL;
		if (Priority.INFO_INT == p.toInt())
			return io.sentry.event.Event.Level.INFO;
		if (Priority.WARN_INT == p.toInt())
			return io.sentry.event.Event.Level.WARNING;
		return io.sentry.event.Event.Level.ERROR;
	}

	private Priority toPriority(String level) {
		if (Util.isEmpty(level, true))
			return null;
		level = level.trim();

		if ("debug".equalsIgnoreCase(level))
			return Priority.DEBUG;
		if ("error".equalsIgnoreCase(level))
			return Priority.ERROR;
		if ("fatal".equalsIgnoreCase(level))
			return Priority.FATAL;
		if ("info".equalsIgnoreCase(level))
			return Priority.INFO;
		if ("trace".equalsIgnoreCase(level))
			return Priority.INFO;
		if ("warn".equalsIgnoreCase(level))
			return Priority.WARN;

		return null;
	}
}
