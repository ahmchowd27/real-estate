package com.ahmed.real_estate.repository;

import com.ahmed.real_estate.model.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {
    // Fetch all available properties
    List<Property> findByAvailableTrue();

    // Fetch all properties (both available and unavailable)
    List<Property> findAll();

    // Fetch properties owned by a specific agent
    List<Property> findByAgentId(Long agentId);

    // Fetch a specific property owned by an agent
    Optional<Property> findByIdAndAgentId(Long id, Long agentId);

    // Search properties by title (for search functionality)
    List<Property> findByTitleContainingIgnoreCase(String title);

    // Search properties by location (useful for filtering)
    List<Property> findByLocationContainingIgnoreCase(String location);
}
