package org.lucee.extension.sentry.log.log4j;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Level;

import lucee.commons.io.log.Log;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.config.Config;

public class SentryUtil {
	public static String getRequestURL(HttpServletRequest req, boolean includeQueryString) {

		StringBuffer sb = req.getRequestURL();
		int maxpos = sb.indexOf("/", 8);

		if (maxpos > -1) {

			if (req.isSecure()) {
				if (sb.substring(maxpos - 4, maxpos).equals(":443"))
					sb.delete(maxpos - 4, maxpos);
			} else {
				if (sb.substring(maxpos - 3, maxpos).equals(":80"))
					sb.delete(maxpos - 3, maxpos);
			}

			if (includeQueryString && !Util.isEmpty(req.getQueryString()))
				sb.append('?').append(req.getQueryString());
		}

		return sb.toString();
	}

	public static Log getLog(Config config, String logName) {

		if (config == null)
			config = CFMLEngineFactory.getInstance().getThreadConfig();

		try {
			return config.getLog(logName);
		} catch (Exception e) {
			return config.getLog("application");
		}
	}

	public static int toLevel(Level l, int dv) {
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

	public static int toLevel(org.apache.log4j.Level l, int dv) {
		if (org.apache.log4j.Level.DEBUG.equals(l))
			return Log.LEVEL_DEBUG;
		if (org.apache.log4j.Level.ERROR.equals(l))
			return Log.LEVEL_ERROR;
		if (org.apache.log4j.Level.INFO.equals(l))
			return Log.LEVEL_INFO;
		if (org.apache.log4j.Level.FATAL.equals(l))
			return Log.LEVEL_FATAL;
		if (org.apache.log4j.Level.TRACE.equals(l))
			return Log.LEVEL_TRACE;
		if (org.apache.log4j.Level.WARN.equals(l))
			return Log.LEVEL_WARN;
		return dv;
	}

	public static String[] appAndMsg(String raw) {
		String[] data = new String[] { "", "" };
		int index = raw.indexOf("->");
		if (index > -1) {
			data[0] = raw.substring(0, index);
			if (data[0].length() > 64)
				data[0] = data[0].substring(0, 63);
			data[1] = raw.substring(index + 2);
		}
		return data;
	}
}
