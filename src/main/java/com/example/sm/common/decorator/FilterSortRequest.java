package com.example.sm.common.decorator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Sort;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterSortRequest<FILTER,SORT>{
    FILTER filter;
    SortRequest<SORT> sort;
    Pagination page;

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public  static class SortRequest<SORT> {
        SORT sortBy;
        Sort.Direction orderBy;
    }
}
