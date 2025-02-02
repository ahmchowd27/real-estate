package com.ahmed.real_estate.controller;
import com.ahmed.real_estate.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.userdetails.UserDetails;
import com.ahmed.real_estate.model.User;
import org.springframework.http.MediaType;
import com.ahmed.real_estate.model.Property;
import com.ahmed.real_estate.model.User;
import com.ahmed.real_estate.repository.PropertyRepository;
import com.ahmed.real_estate.service.S3Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ahmed.real_estate.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyRepository propertyRepository;
    private final S3Service s3Service;
    private final UserRepository userRepository;

    public PropertyController(PropertyRepository propertyRepository, S3Service s3Service, UserRepository userRepository) {
        this.propertyRepository = propertyRepository;
        this.s3Service = s3Service;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Property> getAllProperties() {
        return propertyRepository.findAll().stream().peek(property -> {
            if (property.getImagePaths() != null) {
                property.setImagePaths(property.getImagePaths().stream()
                        .map(s3Service::getSignedImageUrl) // Convert stored keys to signed URLs
                        .collect(Collectors.toList()));
            }
        }).collect(Collectors.toList());
    }

    @GetMapping("/available")
    public List<Property> getAvailableProperties() {
        return propertyRepository.findByAvailableTrue().stream().peek(property -> {
            if (property.getImagePaths() != null) {
                property.setImagePaths(property.getImagePaths().stream()
                        .map(s3Service::getSignedImageUrl)
                        .collect(Collectors.toList()));
            }
        }).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Property> getPropertyById(@PathVariable Long id) {
        return propertyRepository.findById(id)
                .map(property -> {
                    if (property.getImagePaths() != null) {
                        property.setImagePaths(property.getImagePaths().stream()
                                .map(s3Service::getSignedImageUrl)
                                .collect(Collectors.toList()));
                    }
                    return ResponseEntity.ok(property);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


    @PostMapping(value = "/add", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    @PreAuthorize("hasRole('Agent')")
    public ResponseEntity<Property> addProperty(
            @RequestPart("property") String propertyJson,  // Receive JSON as String
            @RequestPart("files") List<MultipartFile> files,
            Authentication authentication) {
        try {
            // Convert JSON string to Property object
            ObjectMapper objectMapper = new ObjectMapper();
            Property property = objectMapper.readValue(propertyJson, Property.class);

            // Get authenticated user
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String username = userDetails.getUsername(); // Extract username

            // Fetch the actual User entity from the database
            Optional<User> agentOpt = userRepository.findByUsername(username);
            if (agentOpt.isEmpty()) {
                return ResponseEntity.status(403).body(null);
            }
            User agent = agentOpt.get(); // Assign correct agent

            // Set agent
            property.setAgent(agent);

            // Upload images to S3
            List<String> imageKeys = files.stream()
                    .map(file -> {
                        try {
                            return s3Service.uploadFile(file);
                        } catch (IOException e) {
                            throw new RuntimeException("File upload failed", e);
                        }
                    })
                    .collect(Collectors.toList());
            property.setImagePaths(imageKeys);

            // Save property
            return ResponseEntity.ok(propertyRepository.save(property));
        } catch (Exception e) {
            e.printStackTrace(); // Log the exact error
            return ResponseEntity.status(500).body(null);
        }
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasRole('Agent')")
    public ResponseEntity<Property> updateProperty(@PathVariable Long id, @RequestPart("property") Property updatedProperty, @RequestPart(value = "files", required = false) List<MultipartFile> files, Authentication authentication) {
        User agent = (User) authentication.getPrincipal();
        return propertyRepository.findByIdAndAgentId(id, agent.getId())
                .map(existingProperty -> {
                    existingProperty.setDescription(updatedProperty.getDescription());
                    existingProperty.setLocation(updatedProperty.getLocation());
                    existingProperty.setPrice(updatedProperty.getPrice());
                    existingProperty.setAvailable(updatedProperty.isAvailable());

                    if (files != null && !files.isEmpty()) {
                        List<String> newImageKeys = files.stream()
                                .map(file -> {
                                    try {
                                        return s3Service.uploadFile(file);
                                    } catch (IOException e) {
                                        throw new RuntimeException("File upload failed", e);
                                    }
                                })
                                .collect(Collectors.toList());
                        existingProperty.setImagePaths(newImageKeys);
                    }

                    return ResponseEntity.ok(propertyRepository.save(existingProperty));
                })
                .orElseGet(() -> ResponseEntity.status(403).build());
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<String> deleteProperty(@PathVariable Long id, Authentication authentication) {
        User agent = (User) authentication.getPrincipal();
        return propertyRepository.findByIdAndAgentId(id, agent.getId())
                .map(property -> {
                    propertyRepository.deleteById(id);
                    return ResponseEntity.ok("Property deleted successfully");
                })
                .orElseGet(() -> ResponseEntity.status(403).body("Unauthorized to delete this property"));
    }
}
