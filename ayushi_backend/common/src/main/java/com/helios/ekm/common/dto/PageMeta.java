package com.helios.ekm.common.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageMeta {
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
}

