package ch.famfrehner.bonflow.masterdata;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleCategoryRepository extends JpaRepository<ArticleCategory, Long> {

    List<ArticleCategory> findAllByOrderBySortOrderAscNameAsc();
}
