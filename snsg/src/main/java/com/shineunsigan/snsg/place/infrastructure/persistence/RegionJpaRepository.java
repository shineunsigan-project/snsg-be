package com.shineunsigan.snsg.place.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shineunsigan.snsg.place.domain.model.Region;

interface RegionJpaRepository extends JpaRepository<Region, String> {

    List<Region> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<Region> findAllByOrderByDisplayOrderAsc();
}
