package com.civictrack.dto;

import com.civictrack.model.Category;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * Bound from multipart/form-data. Priority is NOT accepted from citizens: it is managed by admins
 * (new complaints start as MEDIUM).
 */
@Getter @Setter
public class ComplaintForm {
    @NotBlank @Size(max = 200) private String title;
    @NotBlank @Size(min = 10, max = 5000) private String description;
    @NotNull private Category category;
    @NotNull @DecimalMin("-90") @DecimalMax("90") private Double latitude;
    @NotNull @DecimalMin("-180") @DecimalMax("180") private Double longitude;
    @Size(max = 500) private String address;
    /** Up to 5 photos. */
    private List<MultipartFile> images;
    /** Legacy single-photo field. */
    private MultipartFile image;
}
