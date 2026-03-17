package gov.cms.admin.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ArticleStatusConverter implements AttributeConverter<ArticleStatus, String> {

    @Override
    public String convertToDatabaseColumn(ArticleStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ArticleStatus convertToEntityAttribute(String dbData) {
        return ArticleStatus.fromNullable(dbData);
    }
}