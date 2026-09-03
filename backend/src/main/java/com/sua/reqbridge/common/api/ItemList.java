package com.sua.reqbridge.common.api;

import java.util.List;

public record ItemList<T>(List<T> items) {

	public ItemList {
		items = List.copyOf(items);
	}
}
