package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.OrderItemRequest;
import com.swp.ckms.dto.response.StoreOrderResponse;
import com.swp.ckms.entity.BillingStatement;
import com.swp.ckms.entity.CentralKitchen;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.Invoice;
import com.swp.ckms.entity.OrderDetail;
import com.swp.ckms.entity.Product;
import com.swp.ckms.entity.ProductionPlan;
import com.swp.ckms.entity.StoreOrder;
import com.swp.ckms.entity.User;
import com.swp.ckms.enums.InvoiceStatus;
import com.swp.ckms.enums.OrderStatus;
import com.swp.ckms.exception.business.BusinessRuleViolationException;
import com.swp.ckms.repository.InvoiceRepository;
import com.swp.ckms.repository.StoreOrderRepository;
import com.swp.ckms.repository.UserRepository;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.util.RecipientResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreOrderCoordinationTest {

    @Mock
    private StoreOrderRepository storeOrderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private com.swp.ckms.service.NotificationService notificationService;
    @Mock
    private RecipientResolver recipientResolver;

    @InjectMocks
    private StoreOrderServiceImpl storeOrderService;

    private MockedStatic<SecurityUtils> mockedSecurityUtils;
    private UserContext coordinatorContext;
    private StoreOrder order;
    private User coordinatorUser;
    private CentralKitchen kitchen;

    @BeforeEach
    void setUp() {
        coordinatorContext = UserContext.builder()
                .userId(1L)
                .username("coordinator")
                .scope("SYSTEM")
                .build();

        kitchen = CentralKitchen.builder()
                .kitchenId(1L)
                .maxDailyCapacity(BigDecimal.valueOf(100))
                .build();

        coordinatorUser = User.builder()
                .userId(1L)
                .username("coordinator")
                .kitchen(kitchen)
                .build();

        FranchiseStore store = FranchiseStore.builder().storeId(1L).name("Test Store").build();
        
        order = StoreOrder.builder()
                .orderId(101L)
                .status(OrderStatus.APPROVED)
                .deliveryDate(LocalDate.now().plusDays(1))
                .store(store)
                .createdByUser(User.builder().userId(2L).build())
                .orderDetails(new ArrayList<>())
                .build();

        // Add a detail
        OrderDetail detail = OrderDetail.builder()
                .product(Product.builder().id(1L).build())
                .quantity(10)
                .unitPrice(BigDecimal.valueOf(100))
                .build();
        order.getOrderDetails().add(detail);
        order.setTotalAmount(BigDecimal.valueOf(1000));
    }

    private void mockSecurity(UserContext context) {
        if (mockedSecurityUtils != null) {
            mockedSecurityUtils.close();
        }
        mockedSecurityUtils = mockStatic(SecurityUtils.class);
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserContext).thenReturn(context);
    }

    @Test
    void testRescheduleOrder_Success() {
        mockSecurity(coordinatorContext);
        LocalDate newDate = LocalDate.now().plusDays(2);
        
        when(storeOrderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(userRepository.findById(1L)).thenReturn(Optional.of(coordinatorUser));
        when(storeOrderRepository.sumQuantityByKitchenAndDeliveryDate(eq(1L), eq(newDate))).thenReturn(BigDecimal.valueOf(50));
        when(storeOrderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        StoreOrderResponse response = storeOrderService.rescheduleOrder(101L, newDate);

        assertNotNull(response);
        assertEquals(newDate, order.getDeliveryDate());
        assertEquals(newDate, response.getDeliveryDate());
        assertEquals("Test Store", response.getStoreName());
        verify(storeOrderRepository).save(order);
        mockedSecurityUtils.close();
    }

    @Test
    void testRescheduleOrder_Forbidden_NonCoordinator() {
        UserContext storeContext = UserContext.builder().scope("STORE").build();
        mockSecurity(storeContext);
        
        assertThrows(AccessDeniedException.class, () -> 
            storeOrderService.rescheduleOrder(101L, LocalDate.now().plusDays(2))
        );
        mockedSecurityUtils.close();
    }

    @Test
    void testRescheduleOrder_Error_AlreadyInPlan() {
        mockSecurity(coordinatorContext);
        order.setProductionPlan(new ProductionPlan());
        when(storeOrderRepository.findById(101L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleViolationException.class, () -> 
            storeOrderService.rescheduleOrder(101L, LocalDate.now().plusDays(2))
        );
        mockedSecurityUtils.close();
    }

    @Test
    void testSplitOrder_Success() {
        mockSecurity(coordinatorContext);
        
        // Split 4 units of product 1
        OrderItemRequest splitRequest = OrderItemRequest.builder()
                .productId(1L)
                .quantity(4)
                .build();

        when(storeOrderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(storeOrderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        List<StoreOrderResponse> results = storeOrderService.splitOrder(101L, List.of(splitRequest));

        assertEquals(2, results.size());
        
        // Original order should have 10 - 4 = 6 units
        assertEquals(6, order.getOrderDetails().get(0).getQuantity());
        assertEquals(BigDecimal.valueOf(600).stripTrailingZeros(), order.getTotalAmount().stripTrailingZeros());

        verify(storeOrderRepository, times(2)).save(any());
        mockedSecurityUtils.close();
    }

    @Test
    void testSplitOrder_Error_InBillingStatement() {
        mockSecurity(coordinatorContext);
        Invoice invoice = Invoice.builder().statement(new BillingStatement()).build();
        order.setInvoice(invoice);
        
        when(storeOrderRepository.findById(101L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleViolationException.class, () -> 
            storeOrderService.splitOrder(101L, new ArrayList<>())
        );
        mockedSecurityUtils.close();
    }
}
