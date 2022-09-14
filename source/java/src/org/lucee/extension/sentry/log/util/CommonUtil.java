package org.lucee.extension.sentry.log.util;

import java.util.HashMap;
import java.util.Map;

import io.sentry.SentryEvent;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.util.ListUtil;

public class CommonUtil {
	public static Map<String, String> toMap(String list) {
		Map<String, String> data = new HashMap<>();
		ListUtil util = CFMLEngineFactory.getInstance().getListUtil();
		String[] elements = util.toStringArray(list, ",");
		String[] pair;
		for (String el : elements) {
			pair = util.toStringArray(el, "|");
			if (pair.length == 1) {
				data.put(pair[0].trim(), "");
			} else if (pair.length == 2) {
				data.put(pair[0].trim(), pair[1].trim());
			}
		}
		return data;
	}

	public static void setCustom(SentryEvent event, String dist, String environment, Map<String, String> tags,
			Map<String, String> extras) {
		// set custom values
		if (!Util.isEmpty(dist, true)) {
			event.setDist(dist.trim());
		}
		if (!Util.isEmpty(environment, true)) {
			event.setEnvironment(environment.trim());
		}
		if (extras != null) {
			for (Map.Entry<String, String> e : extras.entrySet()) {
				if (!Util.isEmpty(e.getKey(), true))
					event.setExtra(e.getKey().trim(), e.getValue().trim());
			}
		}
		if (tags != null) {
			for (Map.Entry<String, String> e : tags.entrySet()) {
				if (!Util.isEmpty(e.getKey(), true))
					event.setTag(e.getKey().trim(), e.getValue().trim());
			}
		}

	}
}
