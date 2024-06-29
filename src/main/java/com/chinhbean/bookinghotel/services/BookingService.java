package com.chinhbean.bookinghotel.services;

import com.chinhbean.bookinghotel.components.JwtTokenUtils;
import com.chinhbean.bookinghotel.dtos.BookingDTO;
import com.chinhbean.bookinghotel.dtos.BookingDetailDTO;
import com.chinhbean.bookinghotel.entities.*;
import com.chinhbean.bookinghotel.enums.BookingStatus;
import com.chinhbean.bookinghotel.exceptions.DataNotFoundException;
import com.chinhbean.bookinghotel.exceptions.PermissionDenyException;
import com.chinhbean.bookinghotel.repositories.IBookingRepository;
import com.chinhbean.bookinghotel.repositories.IRoomTypeRepository;
import com.chinhbean.bookinghotel.repositories.IUserRepository;
import com.chinhbean.bookinghotel.responses.BookingResponse;
import com.chinhbean.bookinghotel.utils.MessageKeys;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@EnableAsync
public class BookingService implements IBookingService {

    private final IBookingRepository bookingRepository;
    private final IUserRepository IUserRepository;
    private final JwtTokenUtils jwtTokenUtils;
    private final IRoomTypeRepository roomTypeRepository;

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Transactional
    @Override
    public Booking createBooking(BookingDTO bookingDTO) {
        User user = null;
        Booking booking = null;
        if (bookingDTO.getUserId() != null) {
            user = IUserRepository.findById(bookingDTO.getUserId()).orElse(null);
            booking = getBooking(bookingDTO, user);
            if (user == null) {
                logger.error("User with ID: {} does not exist.", bookingDTO.getUserId());
                return null;
            }
        } else {
            user = IUserRepository.findByFullName("guest").orElse(null);
            booking = getBooking(bookingDTO, user);
        }

        Set<BookingDetails> bookingDetails = bookingDTO.getBookingDetails().stream()
                .map(this::convertToEntity)
                .collect(Collectors.toSet());
        booking.setBookingDetails(bookingDetails);

        // Set expiration date to current time + 300 seconds
        booking.setExpirationDate(LocalDateTime.now().plusSeconds(30));

        Booking savedBooking = bookingRepository.save(booking);

        // Schedule a task to delete the booking after 300 seconds if still PENDING
        scheduler.schedule(() -> deleteBookingIfPending(savedBooking.getBookingId()), 300, TimeUnit.SECONDS);

        return booking;
    }

    @Async
    public CompletableFuture<Void> deleteBookingIfPending(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking != null && BookingStatus.PENDING.equals(booking.getStatus())) {
            bookingRepository.delete(booking);
            logger.info("Deleted expired booking with ID: {}", bookingId);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Transactional
    @Override
    public Page<BookingResponse> getListBooking(String token, int page, int size) throws DataNotFoundException, PermissionDenyException {
        logger.info("Fetching all bookings from the database.");
        Pageable pageable = PageRequest.of(page, size);
        Page<Booking> bookings;
        String userRole = jwtTokenUtils.extractUserRole(token);
        if (Role.ADMIN.equals(userRole)) {
            bookings = bookingRepository.findAll(pageable);
        } else if (Role.PARTNER.equals(userRole)) {
            Long userId = jwtTokenUtils.extractUserId(token);
            bookings = bookingRepository.findAllByPartnerId(userId, pageable);
        } else if (Role.CUSTOMER.equals(userRole)) {
            Long userId = jwtTokenUtils.extractUserId(token);
            bookings = bookingRepository.findAllByUserId(userId, pageable);
        } else {
            logger.error("User does not have permission to view bookings.");
            throw new PermissionDenyException(MessageKeys.USER_DOES_NOT_HAVE_PERMISSION_TO_VIEW_BOOKINGS);
        }
        if (bookings.isEmpty()) {
            logger.warn("No bookings found in the database.");
            throw new DataNotFoundException(MessageKeys.NO_BOOKINGS_FOUND);
        }
        logger.info("Successfully retrieved all bookings.");
        return bookings.map(BookingResponse::fromBooking);
    }

    private static Booking getBooking(BookingDTO bookingDTO, User user) {
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setEmail(bookingDTO.getEmail());
        booking.setPhoneNumber(bookingDTO.getPhoneNumber());
        booking.setFullName(bookingDTO.getFullName());
        booking.setTotalPrice(bookingDTO.getTotalPrice());
        booking.setCheckInDate(bookingDTO.getCheckInDate());
        booking.setCheckOutDate(bookingDTO.getCheckOutDate());
        booking.setStatus(BookingStatus.PENDING);
        booking.setCouponId(bookingDTO.getCouponId());
        booking.setNote(bookingDTO.getNote());
        booking.setPaymentMethod(bookingDTO.getPaymentMethod());
        booking.setExpirationDate(LocalDateTime.now().plusSeconds(300)); // Set expiration date to current time + 300 seconds
        booking.setBookingDate(LocalDateTime.now());
        return booking;
    }

    @Transactional
    @Override
    public BookingResponse getBookingDetail(Long bookingId) throws DataNotFoundException {
        logger.info("Fetching details for booking with ID: {}", bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> {
                    logger.error("Booking with ID: {} does not exist.", bookingId);
                    return new DataNotFoundException(MessageKeys.NO_BOOKINGS_FOUND);
                });
        logger.info("Successfully retrieved details for booking with ID: {}", bookingId);
        return BookingResponse.fromBooking(booking);
    }

    @Transactional
    @Override
    public Booking updateBooking(Long bookingId, BookingDTO bookingDTO, String token) throws DataNotFoundException {
        logger.info("Updating booking with ID: {}", bookingId);
        Long userId = jwtTokenUtils.extractUserId(token);
        User user = IUserRepository.findById(userId).orElse(null);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new DataNotFoundException(MessageKeys.NO_BOOKINGS_FOUND));

        booking.setUser(user);
        if (bookingDTO.getTotalPrice() != null) {
            booking.setTotalPrice(bookingDTO.getTotalPrice());
        }
        if (bookingDTO.getCheckInDate() != null) {
            booking.setCheckInDate(bookingDTO.getCheckInDate());
        }
        if (bookingDTO.getCheckOutDate() != null) {
            booking.setCheckOutDate(bookingDTO.getCheckOutDate());
        }
        if (bookingDTO.getCouponId() != null) {
            booking.setCouponId(bookingDTO.getCouponId());
        }
        if (bookingDTO.getNote() != null) {
            booking.setNote(bookingDTO.getNote());
        }
        if (bookingDTO.getPaymentMethod() != null) {
            booking.setPaymentMethod(bookingDTO.getPaymentMethod());
        }


        if (bookingDTO.getBookingDetails() != null) {
            Set<BookingDetails> bookingDetails = bookingDTO.getBookingDetails().stream()
                    .map(this::convertToEntity)
                    .collect(Collectors.toSet());
            booking.setBookingDetails(bookingDetails);
        }

        logger.info("Booking with ID: {} updated successfully.", bookingId);
        return bookingRepository.save(booking);
    }

    @Transactional
    @Override
    public void updateStatus(Long bookingId, BookingStatus newStatus, String token) throws DataNotFoundException, PermissionDenyException {
        logger.info("Updating status for booking with ID: {}", bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new DataNotFoundException(MessageKeys.NO_BOOKINGS_FOUND));
        String userRole = jwtTokenUtils.extractUserRole(token);
        if (Role.ADMIN.equals(userRole)) {
            booking.setStatus(newStatus);
        } else if (Role.CUSTOMER.equals(userRole)) {
            if (newStatus == BookingStatus.CANCELLED) {
                booking.setStatus(newStatus);
            } else {
                throw new PermissionDenyException(MessageKeys.USER_CANNOT_CHANGE_STATUS_TO + newStatus.toString());
            }
        } else if (Role.PARTNER.equals(userRole)) {
            if (newStatus == BookingStatus.CONFIRMED) {
                booking.setStatus(newStatus);
            } else {
                throw new PermissionDenyException(MessageKeys.USER_CANNOT_CHANGE_STATUS_TO + newStatus.toString());
            }
        } else {
            throw new PermissionDenyException(MessageKeys.USER_DOES_NOT_HAVE_PERMISSION_TO_CHANGE_STATUS);
        }
        bookingRepository.save(booking);
        logger.info("Status for booking with ID: {} updated successfully.", bookingId);
    }

    private BookingDetails convertToEntity(BookingDetailDTO detailDTO) {
        BookingDetails detail = new BookingDetails();
        RoomType roomType = roomTypeRepository.findById(detailDTO.getRoomTypeId()).orElse(null);
        detail.setRoomType(roomType);
        detail.setPrice(detailDTO.getPrice());
        detail.setNumberOfRooms(detailDTO.getNumberOfRooms());
        detail.setTotalMoney(detailDTO.getTotalMoney());
        return detail;
    }
}
