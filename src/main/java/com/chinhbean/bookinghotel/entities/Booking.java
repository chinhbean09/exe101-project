package com.chinhbean.bookinghotel.entities;

import com.chinhbean.bookinghotel.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(nullable = false)
    private String note;

    @Column(name = "booking_date", nullable = false)
    private LocalDateTime bookingDate;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "expiration_date")
    private LocalDateTime expirationDate;

    @OneToMany(mappedBy = "booking")
    private Set<BookingDetails> bookingDetails = new LinkedHashSet<>();

    private String fullName;

    private Long phoneNumber;

    private String email;

}



