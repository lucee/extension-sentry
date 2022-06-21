package org.lucee.extension.sentry.log.log4j;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Iterator;

import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import io.sentry.DateUtils;
import io.sentry.Hub;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import lucee.commons.io.log.Log;
import lucee.commons.io.res.Resource;
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

public class SentryAppenderLog4j1 implements Appender {

	private static BIF contractPath;
	private static final Object token = new Object();
	private Log local = null;
	// private io.sentry.log4j.SentryAppender sentry = null;
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

	private Config config;
	private String dsn;
	private boolean isInit;
	private Hub hub;
	private boolean debug = true;
	private String logger;

	public SentryAppenderLog4j1() {
		this(null);
	}

	public SentryAppenderLog4j1(String dsn) {
		this.dsn = dsn;
		try {
			config = CFMLEngineFactory.getInstance().getThreadConfig();
		} catch (Exception e) {
		}
	}

	private void init() {
		if (!isInit) {
			synchronized (token) {
				if (!isInit) {

					SentryOptions so = new SentryOptions();

					so.setDsn(dsn);
					so.setDebug(debug);
					so.setEnableExternalConfiguration(true);
					so.setSentryClientName("lucee.sentry.log4j1");
					hub = new Hub(so);

					isInit = true;
				}
			}
		}
	}

	public void setDsn(String dsn) {
		if (!Util.isEmpty(dsn))
			this.dsn = dsn;
	}

	protected SentryEvent createEvent(final LoggingEvent loggingEvent) {
		final SentryEvent event = new SentryEvent(DateUtils.getDateTime(loggingEvent.getTimeStamp()));
		final Message message = new Message();

		String[] data = SentryUtil.appAndMsg(loggingEvent.getMessage() + "");

		message.setMessage(data[1]);
		event.setMessage(message);
		event.setLogger(loggingEvent.getLoggerName());
		event.setLevel(toSentryLevel(loggingEvent.getLevel(), SentryLevel.ERROR));
		event.setExtra("Application", data[0]);

		ThrowableInformation throwableInformation = loggingEvent.getThrowableInformation();
		if (throwableInformation != null) {
			event.setThrowable(throwableInformation.getThrowable());
		}

		if (loggingEvent.getThreadName() != null) {
			event.setExtra("thread_name", loggingEvent.getThreadName());
		}

		// caller
		PageContext pc = CFMLEngineFactory.getInstance().getThreadPageContext();
		if (pc != null) {
			Throwable callerException = new Throwable();
			String caller = getCaller(pc, callerException);
			if (caller != null)
				event.setExtra("caller", caller);
		}

		return event;
	}

	private String getCaller(PageContext pc, Throwable t) {
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		Cast caster = engine.getCastUtil();

		PageException pe = caster.toPageException(t);
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
		// full stacktrace
		return toString(t.getStackTrace());
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

			if (ste.getClassName().startsWith("org.apache.log4j."))
				continue;
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

		Config config = getConfig(null);
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

	private Log local() {
		if (local == null) {
			local = SentryUtil.getLog(config, logger);
		}
		return local;
	}

	@Override
	public void addFilter(Filter filter) {

	}

	@Override
	public void clearFilters() {
	}

	@Override
	public Filter getFilter() {
		return null;
	}

	@Override
	public void close() {
	}

	@Override
	public void doAppend(LoggingEvent event) {
		init();
		boolean done = false;
		if (hub.isEnabled() && event.getLevel().isGreaterOrEqual(threshold)) {
			try {
				hub.captureEvent(createEvent(event));
				done = true;
			} catch (Exception e) {
			}
		}
		if (!done) {
			System.err.println("llllll " + logger + " llllll");
			Log l = local();
			if (l != null) {

				ThrowableInformation throwableInformation = event.getThrowableInformation();
				String msg = event.getMessage().toString();

				int level = SentryUtil.toLevel(event.getLevel(), Log.LEVEL_ERROR);
				System.err.println(l.toString());
				System.err.println("level:" + level);
				if (throwableInformation != null) {
					l.log(level, msg, throwableInformation.getThrowable());

				} else {
					l.log(level, msg, "");
				}
			}
		}
	}

	@Override
	public ErrorHandler getErrorHandler() {
		return null;
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

	public void setLogger(String logger) {
		this.logger = logger;
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
		return false;
	}

	@Override
	public void setErrorHandler(ErrorHandler eh) {
	}

	private static SentryLevel toSentryLevel(final Level l, SentryLevel dv) {
		if (Level.DEBUG.equals(l))
			return SentryLevel.DEBUG;
		if (Level.ERROR.equals(l))
			return SentryLevel.ERROR;
		if (Level.INFO.equals(l))
			return SentryLevel.INFO;
		if (Level.FATAL.equals(l))
			return SentryLevel.FATAL;
		if (Level.TRACE.equals(l))
			return SentryLevel.INFO;
		if (Level.WARN.equals(l))
			return SentryLevel.WARNING;
		return dv;
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

	public static String contractPath(PageContext pc, String abs) throws PageException {
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
