package com.korur.oba.connect.push.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.push.dto.request.PushDeviceRegisterRequest;
import com.korur.oba.connect.push.dto.response.PushDeviceResponse;
import com.korur.oba.connect.push.entity.PushDevice;

/**
 * Maps push device entities to DTOs and applies registrations to them.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PushDeviceMapper {

    /**
     * Maps a push device to its response DTO, without its token.
     */
    PushDeviceResponse toResponse(PushDevice pushDevice);

    /**
     * Applies a registration to a device as a full update, so a null device name clears the stored
     * one. The owner and the registration time are set by the service, because they come from the
     * caller and the clock, not from the request.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "lastRegisteredAt", ignore = true)
    void updateEntity(@MappingTarget PushDevice pushDevice, PushDeviceRegisterRequest request);
}
