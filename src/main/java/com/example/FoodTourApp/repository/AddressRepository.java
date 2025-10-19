package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Address;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Integer> {

    // Các method cho địa chỉ user
    List<Address> findByUser(User user);

    List<Address> findByUserOrderByIsDefaultDesc(User user);

    Optional<Address> findByUserAndIsDefaultTrue(User user);

    List<Address> findByUserAndAddressType(User user, Address.AddressType addressType);

    // Các method cho địa chỉ shop
    List<Address> findByAddressType(Address.AddressType addressType);

    List<Address> findByCity(String city);

    List<Address> findByCityAndAddressType(String city, Address.AddressType addressType);

    List<Address> findByDistrictAndCity(String district, String city);

    // Tìm địa chỉ theo tọa độ (cho việc tìm shop gần nhất)
    List<Address> findByLatitudeBetweenAndLongitudeBetween(
        Double minLat, Double maxLat, Double minLng, Double maxLng);
}
