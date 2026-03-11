package com.onlinestore.users.repository;

import com.onlinestore.users.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

    boolean existsByIdAndUser_Id(Long id, Long userId);
}
