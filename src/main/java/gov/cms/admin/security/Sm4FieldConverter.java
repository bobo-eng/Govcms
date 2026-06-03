package gov.cms.admin.security;

import gov.cms.admin.config.SpringContextHolder;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class Sm4FieldConverter implements AttributeConverter<String, String> {

    private volatile Sm4Encryptor encryptor;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        return getEncryptor().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        return getEncryptor().decrypt(dbData);
    }

    private Sm4Encryptor getEncryptor() {
        if (encryptor == null) {
            encryptor = SpringContextHolder.getBean(Sm4Encryptor.class);
        }
        if (encryptor == null) {
            throw new IllegalStateException("Sm4Encryptor not available — SpringContextHolder not initialized");
        }
        return encryptor;
    }
}
