package org.lucee.extension.sentry.log.log4j;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.logging.log4j.core.Appender;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.config.Config;
import lucee.runtime.db.ClassDefinition;
import lucee.runtime.exp.PageException;

public class Log4j2Engine {
	private static final Class[] ARGS_EMPTY = new Class[] {};
	private static final Class[] ARGS_APP_CLS_DEF = new Class[] { String.class };
	private static final Class[] ARGS_APP = new Class[] { Config.class, Object.class, String.class,
			ClassDefinition.class, Map.class };
	private static Object eng;
	private static Method appenderClassDefintion;
	private static Method getAppender;

	// public final Object getAppender(Config config, Object layout, String name,
	// ClassDefinition cd, Map<String, String> appenderArgs) {

	public static final Appender getAppender(Config config, Object layout, String name, String appender,
			Map<String, String> appenderArgs) throws PageException {
		CFMLEngine cfmleng = CFMLEngineFactory.getInstance();
		if (config == null)
			config = cfmleng.getThreadConfig();

		try {
			Object eng = eng(config);
			if (getAppender == null
					|| getAppender.getDeclaringClass().getClassLoader() != cfmleng.getClass().getClassLoader()) {
				getAppender = eng.getClass().getMethod("getAppender", ARGS_APP);
			}
			return (Appender) getAppender.invoke(eng,
					new Object[] { config, layout, name, appenderClassDefintion(config, appender), appenderArgs });
		} catch (Exception e) {
			throw cfmleng.getCastUtil().toPageException(e);
		}
	}

	public static ClassDefinition appenderClassDefintion(Config config, String className) throws PageException {
		CFMLEngine cfmleng = CFMLEngineFactory.getInstance();
		if (config == null)
			config = cfmleng.getThreadConfig();
		try {
			Object eng = eng(config);
			if (appenderClassDefintion == null || appenderClassDefintion.getDeclaringClass().getClassLoader() != cfmleng
					.getClass().getClassLoader()) {
				appenderClassDefintion = eng.getClass().getMethod("appenderClassDefintion", ARGS_APP_CLS_DEF);
			}
			return (ClassDefinition) appenderClassDefintion.invoke(eng, new Object[] { className });
		} catch (Exception e) {
			throw cfmleng.getCastUtil().toPageException(e);
		}
	}

	private static Object eng(Config config) throws PageException {
		CFMLEngine cfmleng = CFMLEngineFactory.getInstance();
		if (config == null)
			config = cfmleng.getThreadConfig();
		System.err.println("step 1");
		if (eng == null || eng.getClass().getClassLoader() != cfmleng.getClass().getClassLoader()) {
			System.err.println("step 2");
			try {
				Method m = config.getClass().getMethod("getLogEngine", ARGS_EMPTY);
				System.err.println("step 3");
				System.err.println(m);
				eng = m.invoke(config, new Object[] {});
				System.err.println("step 4");
				System.err.println(eng);
			} catch (Exception e) {
				throw cfmleng.getCastUtil().toPageException(e);
			}
		}
		return eng;
	}
}
