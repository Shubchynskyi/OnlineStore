package com.onlinestore.search.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.common.dto.PageResponse;
import com.onlinestore.search.dto.ProductSearchRequest;
import com.onlinestore.search.dto.ProductSearchResult;
import com.onlinestore.search.service.SearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private SearchService searchService;

    @Mock
    private ProductSearchRequest request;

    private SearchController searchController;

    @BeforeEach
    void setUp() {
        searchController = new SearchController(searchService);
    }

    @Test
    void searchShouldDelegateToSearchService() {
        var pageable = Pageable.unpaged();
        var response = new PageResponse<ProductSearchResult>(List.of(), 0, 20, 0, 0, true);
        when(searchService.search(request, pageable)).thenReturn(response);

        assertThat(searchController.search(request, pageable)).isSameAs(response);

        verify(searchService).search(request, pageable);
    }

    @Test
    void suggestShouldDelegateToSearchService() {
        var suggestions = List.of("phone", "photo");
        when(searchService.suggest("ph")).thenReturn(suggestions);

        assertThat(searchController.suggest("ph")).isSameAs(suggestions);

        verify(searchService).suggest("ph");
    }
}
