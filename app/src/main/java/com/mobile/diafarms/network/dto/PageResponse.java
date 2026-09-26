package com.mobile.diafarms.network.dto;

import java.util.List;

/** Miroir de PaginatedResponse côté backend. */
public class PageResponse<T> {
    public List<T> data;
    public int currentPage;
    public int totalPages;
    public long totalItems;
}
