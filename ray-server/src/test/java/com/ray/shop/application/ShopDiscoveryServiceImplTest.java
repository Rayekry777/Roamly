package com.ray.shop.application;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.ray.entity.*;
import com.ray.mapper.*;
import com.ray.service.*;
import com.ray.service.impl.ShopDiscoveryServiceImpl;
import com.ray.service.discovery.*;
import com.ray.exception.BusinessException;
import com.ray.vo.LocationContextVO;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
class ShopDiscoveryServiceImplTest {
    ShopDiscoveryMapper mapper = mock(ShopDiscoveryMapper.class);
    ShopTypeMapper types = mock(ShopTypeMapper.class);
    LocationService location = mock(LocationService.class);
    CityService cities = mock(CityService.class);
    ShopDiscoveryServiceImpl service;
    @BeforeEach @SuppressWarnings("unchecked")
    void setup() {
        var query = mock(LambdaQueryChainWrapper.class);
        when(cities.lambdaQuery()).thenReturn(query);
        when(query.eq(any(SFunction.class),any())).thenReturn(query);
        when(query.exists()).thenReturn(true);
        when(types.selectById(1L)).thenReturn(new ShopType().setId(1L));
        when(types.selectById(2L)).thenReturn(new ShopType().setId(2L));
        when(types.selectById(104L)).thenReturn(new ShopType().setId(104L).setParentId(1L));
        when(mapper.page(any())).thenReturn(List.of());
        service = new ShopDiscoveryServiceImpl(mapper,types,location,cities);
    }
    @Test void refusesCrossCategoryLeafAndInvalidCoordinates() {
        assertEquals("INVALID_CATEGORY", assertThrows(BusinessException.class, () -> service.discover("330100",2L,104L,null,"RECOMMENDED",1,10,null,null)).code());
        assertEquals("INCOMPLETE_COORDINATES", assertThrows(BusinessException.class, () -> service.discover("330100",1L,null,null,"DISTANCE",1,10,120D,null)).code());
        assertEquals("DISTANCE_REQUIRES_COORDINATES", assertThrows(BusinessException.class, () -> service.discover("330100",1L,null,null,"DISTANCE",1,10,null,null)).code());
        assertThrows(BusinessException.class, () -> service.discover("330100",1L,null,null,"RECOMMENDED",1,10,Double.NaN,30D));
        verifyNoInteractions(mapper);
    }
    @Test void realLocationOverridesCityAndPassesDistrictWithoutChangingCategory() {
        when(location.resolve(any())).thenReturn(new LocationContextVO("630100","西宁","630105","城北区",null,"西宁",101.7,36.7,30D,"READY"));
        service.discover("330100",1L,104L," 咖啡 ","RECOMMENDED",2,10,101.7,36.7);
        var capture = ArgumentCaptor.forClass(DiscoveryQuery.class); verify(mapper).page(capture.capture());
        var query = capture.getValue(); assertEquals("630100",query.cityCode()); assertEquals("630105",query.districtCode());
        assertEquals(1L,query.categoryId()); assertEquals(104L,query.typeId()); assertEquals("咖啡",query.keyword()); assertEquals(10,query.offset());
        verify(mapper,never()).vouchers(anyList(),any());
    }
    @Test void manualCityDoesNotUseDistrictOrCoordinates() {
        service.discover("630100",2L,null,null,"RECOMMENDED",1,10,null,null);
        var capture = ArgumentCaptor.forClass(DiscoveryQuery.class); verify(mapper).page(capture.capture());
        assertNull(capture.getValue().districtCode()); verifyNoInteractions(location);
    }
    @Test void selectsBestsellerThenCheapestWithoutDuplicatesAndPrefersSearchMatches() {
        var hot = voucher(1,100,9900,false); var cheap = voucher(2,10,1000,false); var match = voucher(3,1,5000,true);
        assertEquals(List.of(hot,cheap),ShopDiscoveryServiceImpl.selectVouchers(List.of(cheap,hot)));
        assertEquals(List.of(match,cheap),ShopDiscoveryServiceImpl.selectVouchers(List.of(hot,cheap,match)));
        assertEquals(List.of(hot),ShopDiscoveryServiceImpl.selectVouchers(List.of(hot)));
        assertTrue(ShopDiscoveryServiceImpl.selectVouchers(List.of()).isEmpty());
    }
    private DiscoveryVoucherRow voucher(long id,int sold,long price,boolean match) {
        var v = new DiscoveryVoucherRow(); v.setId(id);v.setSoldCount(sold);v.setPriceAmount(price);v.setKeywordMatched(match);return v;
    }
}
