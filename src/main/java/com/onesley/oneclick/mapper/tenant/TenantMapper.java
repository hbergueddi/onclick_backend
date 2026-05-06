package com.onesley.oneclick.mapper.tenant;

import com.onesley.oneclick.dto.tenant.TenantDto;
import com.onesley.oneclick.entity.tenant.Tenant;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface TenantMapper {

    TenantDto toDto(Tenant entity);

    List<TenantDto> toDtoList(List<Tenant> entities);
}
