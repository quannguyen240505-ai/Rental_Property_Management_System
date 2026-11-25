package com.rental.property.dto.request;

import com.rental.property.entity.ViewingStatus;

public record UpdateViewingResultRequest(ViewingStatus result, String notes) { }
