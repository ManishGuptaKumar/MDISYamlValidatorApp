package org.nwg.mdis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

public class YamlValidationService {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    public String validate(String yamlText) {
        String schemaPath = "/" + FileService.getTagValue(yamlText, "template_id") + ".json";
        try (InputStream schemaStream = getClass().getResourceAsStream(schemaPath)) {
            if (schemaStream == null) return "Schema file not found in Resource Folder: " + schemaPath;
            JsonNode schemaNode = yamlMapper.readTree(schemaStream);
            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            JsonNode data = yamlMapper.readTree(yamlText);
            Set<ValidationMessage> errs = schema.validate(data);
            if (errs.isEmpty()) {
                return "YAML is valid!";
            }
            return "❌ Validation Errors:\n" +
                    errs.stream().map(ValidationMessage::getMessage).collect(Collectors.joining("\n"));
        } catch (Exception ex) {
            return "❌ Error during validation:\n" + ex.getMessage();
        }
    }
}
