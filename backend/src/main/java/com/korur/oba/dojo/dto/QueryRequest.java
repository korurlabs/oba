package com.korur.oba.dojo.dto;

// 1. Framework-level
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

@Schema(description = "Base query parameters for paginated requests")
@Getter
@Setter
@NoArgsConstructor
public abstract class QueryRequest {

    @Schema(description = "Page number (1-based)", example = "1", nullable = true)
    @Min(value = 1, message = CommonConstant.PAGE_MIN_MESSAGE)
    private int page = 1;

    @Schema(description = "Number of items per page", example = "25", nullable = true)
    @Min(value = 1, message = CommonConstant.AMOUNT_MIN_MESSAGE)
    @Max(value = 50, message = CommonConstant.AMOUNT_MAX_MESSAGE)
    private int amount = 25;

    @Schema(description = "Field to sort by", example = "name", nullable = true)
    private String sortBy;

    @Schema(description = "Sort direction (asc or desc)", example = "asc", nullable = true)
    @Pattern(regexp = "asc|desc", message = CommonConstant.SORT_DIRECTION_MESSAGE, flags = Pattern.Flag.CASE_INSENSITIVE)
    private String sortDirection;

    public Pageable toPageable() {
        if (sortBy == null || sortDirection == null) {
            return PageRequest.of(page - 1, amount, Sort.by(Sort.Direction.ASC, CommonConstant.ID));
        }
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        return PageRequest.of(page - 1, amount, sort);
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
