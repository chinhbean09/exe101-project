package com.chinhbean.bookinghotel.scheduling;

import com.chinhbean.bookinghotel.entities.Hotel;
import com.chinhbean.bookinghotel.enums.HotelStatus;
import com.chinhbean.bookinghotel.enums.RoomTypeStatus;
import com.chinhbean.bookinghotel.repositories.IHotelRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HotelStatusUpdater {

    private final IHotelRepository hotelRepository;

    public HotelStatusUpdater(IHotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void updateHotelStatus() {
        List<Hotel> hotels = hotelRepository.findAll();
        for (Hotel hotel : hotels) {
            boolean allRoomTypesAreFull = hotel.getRoomTypes().stream()
                    .allMatch(roomType -> roomType.getStatus() != RoomTypeStatus.AVAILABLE);
            if (allRoomTypesAreFull) {
                hotel.setStatus(HotelStatus.CLOSED);
            } else if (hotel.getStatus() == HotelStatus.CLOSED) {
                hotel.setStatus(HotelStatus.ACTIVE);
            }
            hotelRepository.save(hotel);
        }
    }
}
