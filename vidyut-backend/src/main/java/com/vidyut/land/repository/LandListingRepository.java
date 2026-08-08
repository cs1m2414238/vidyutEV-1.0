package com.vidyut.land.repository;

import com.vidyut.land.entity.LandListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LandListingRepository extends JpaRepository<LandListing, Long> {
    List<LandListing> findByHostUserId(Long hostUserId);
}
