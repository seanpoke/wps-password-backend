package com.docauth.dto;

import java.util.List;

/** 通用分页返回结构（页码从 1 开始） */
public class PageResult<T> {
    private List<T> list;
    private long total;
    private int page;
    private int size;

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<T> getList() { return list; }
    public void setList(List<T> v) { list = v; }
    public long getTotal() { return total; }
    public void setTotal(long v) { total = v; }
    public int getPage() { return page; }
    public void setPage(int v) { page = v; }
    public int getSize() { return size; }
    public void setSize(int v) { size = v; }
}
