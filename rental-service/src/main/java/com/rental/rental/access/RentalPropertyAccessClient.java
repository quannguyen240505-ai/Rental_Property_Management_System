package com.rental.rental.access;

import java.util.UUID;

public interface RentalPropertyAccessClient {

    void requireRoomAccess(UUID roomId, String userId);

}
