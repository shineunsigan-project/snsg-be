package com.shineunsigan.snsg.place.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.place.domain.model.Region;
import com.shineunsigan.snsg.place.domain.repository.RegionRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RegionRepositoryImpl implements RegionRepository {

    private final RegionJpaRepository regionJpaRepository;

    @Override
    public List<Region> findAllActive() {
        return regionJpaRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    @Override
    public List<Region> findAll() {
        return regionJpaRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Override
    public Optional<Region> findByCode(String code) {
        return regionJpaRepository.findById(code);
    }
}
