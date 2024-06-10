package com.chinhbean.bookinghotel.services;

import com.chinhbean.bookinghotel.dtos.HotelDTO;
import com.chinhbean.bookinghotel.entities.Hotel;
import com.chinhbean.bookinghotel.entities.User;
import com.chinhbean.bookinghotel.enums.HotelStatus;
import com.chinhbean.bookinghotel.exceptions.DataNotFoundException;
import com.chinhbean.bookinghotel.exceptions.PermissionDenyException;
import com.chinhbean.bookinghotel.responses.HotelResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public interface IHotelService {
    Page<HotelResponse> getAllHotels(String token, int page, int size);

    HotelResponse getHotelDetail(Long hotelId) throws DataNotFoundException;

    HotelResponse createHotel(HotelDTO hotelDTO, String token) throws DataNotFoundException;

    HotelResponse updateHotel(Long hotelId, HotelDTO updateDTO, String token) throws DataNotFoundException;

    User getUserDetailsFromToken(String token) throws DataNotFoundException;

    void updateStatus(Long hotelId, HotelStatus newStatus, String token) throws DataNotFoundException, PermissionDenyException;

    Hotel uploadBusinessLicense(Long hotelId, MultipartFile file) throws IOException, DataNotFoundException;
}
