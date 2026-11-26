package com.rental.property.service;

import com.rental.property.dto.response.ContractEligibilityResponse;
import com.rental.property.entity.Room;
import com.rental.property.entity.RoomHold;
import com.rental.property.entity.RoomHoldStatus;
import com.rental.property.entity.RoomStatus;
import com.rental.property.repository.RoomHoldRepository;
import com.rental.property.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class RoomContractEligibilityService {
    private final RoomRepository rooms;
    private final RoomHoldRepository holds;

    public RoomContractEligibilityService(RoomRepository rooms, RoomHoldRepository holds) {
        this.rooms = rooms;
        this.holds = holds;
    }

    /**
     * This is a read-only, point-in-time snapshot. Rental must revalidate before
     * activation; checking eligibility is NOT a distributed lock or reservation.
     */
    @Transactional(readOnly = true)
    public ContractEligibilityResponse check(UUID roomId) {
        Room room = rooms.findById(roomId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND"));
        UUID propertyId = room.getProperty().getId();
        RoomStatus status = room.getStatus();

        if (status == RoomStatus.AVAILABLE) {
            // An APPROVED Hold on an AVAILABLE room indicates inconsistent state.
            boolean inconsistent = !approvedHolds(roomId).isEmpty();
            return new ContractEligibilityResponse(roomId, propertyId, status,
                    !inconsistent, inconsistent ? "ROOM_HOLD_INCONSISTENT" : null,
                    false, null, null, null);
        }

        if (status == RoomStatus.RESERVED) {
            List<RoomHold> approved = approvedHolds(roomId);
            if (approved.size() != 1) {
                return new ContractEligibilityResponse(roomId, propertyId, status,
                        false, approved.isEmpty() ? "APPROVED_HOLD_NOT_FOUND" : "MULTIPLE_APPROVED_HOLDS",
                        true, null, null, null);
            }
            RoomHold hold = approved.get(0);
            if (hold.getId() == null || hold.getCustomerId() == null
                    || hold.getApprovedBy() == null || hold.getApprovedAt() == null
                    || hold.getStatus() != RoomHoldStatus.APPROVED) {
                return new ContractEligibilityResponse(roomId, propertyId, status,
                        false, "APPROVED_HOLD_INCOMPLETE", true, null, null, null);
            }
            return new ContractEligibilityResponse(roomId, propertyId, status,
                    true, null, true, hold.getId(), RoomHoldStatus.APPROVED,
                    hold.getCustomerId());
        }

        String reason = status == RoomStatus.RESERVED_PENDING
                ? "ROOM_PENDING_VERIFICATION"
                : status == RoomStatus.OCCUPIED ? "ROOM_OCCUPIED" : "INVALID_ROOM_STATUS";
        return new ContractEligibilityResponse(roomId, propertyId, status,
                false, reason, false, null, null, null);
    }

    private List<RoomHold> approvedHolds(UUID roomId) {
        return holds.findByRoom_IdAndStatus(roomId, RoomHoldStatus.APPROVED);
    }
}
