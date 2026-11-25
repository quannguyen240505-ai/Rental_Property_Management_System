package com.rental.property.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateViewingRequest(UUID roomId, LocalDateTime startAt, LocalDateTime endAt) { }
