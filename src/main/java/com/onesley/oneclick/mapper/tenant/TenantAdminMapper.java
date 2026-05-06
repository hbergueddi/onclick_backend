package com.onesley.oneclick.mapper.tenant;

import com.onesley.oneclick.dto.tenant.TenantAdminDto;
import com.onesley.oneclick.entity.tenant.TenantAdmin;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface TenantAdminMapper {

    TenantAdminDto toDto(TenantAdmin entity);

    List<TenantAdminDto> toDtoList(List<TenantAdmin> entities);
}
