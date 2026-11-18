package com.korur.oba.dojo.dto;

// 1. Framework-level
import org.springframework.data.domain.Sort;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;

@Schema(description = "Base query parameters for sorted requests")
@Getter
@Setter
@NoArgsConstructor
public abstract class QuerySortRequest {

    @Schema(description = "Field to sort by", example = "name", nullable = true)
    private String sortBy = CommonConstant.ID;

    @Schema(description = "Sort direction (asc or desc)", example = "asc", nullable = true)
    @Pattern(regexp = "asc|desc", message = CommonConstant.SORT_DIRECTION_MESSAGE, flags = Pattern.Flag.CASE_INSENSITIVE)
    private String sortDirection = "asc";

    public Sort toSort() {
        return Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append("{");

        List<Field> allFields = getAllFields(getClass());
        boolean first = true;

        for (Field field : allFields) {
            try {
                field.setAccessible(true);
                Object value = field.get(this);

                if (!first) {
                    sb.append(", ");
                }
                first = false;

                sb.append(field.getName()).append("=");

                if (value == null) {
                    sb.append("null");
                } else if (value instanceof String) {
                    sb.append("'").append(value).append("'");
                } else {
                    sb.append(value);
                }
            } catch (IllegalAccessException e) {
                sb.append(field.getName()).append("=<inaccessible>");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();

        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }

        return fields;
    }
}
