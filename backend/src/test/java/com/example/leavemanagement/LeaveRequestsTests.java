package com.example.leavemanagement;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import com.example.leavemanagement.service.LeaveRequestService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

// Runs against a real, throwaway PostgreSQL started by Testcontainers.
// (Docker must be available on the machine running the tests.)
@SpringBootTest
@Testcontainers
class LeaveRequestsTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private LeaveRequestRepository leaveRequests;

    @Test
    void create_WithinQuota_Succeeds() {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 3)); // 3 days, well within the quota

        // Act
        LeaveRequest result = leaveRequestService.create(dto);

        // Assert
        Assertions.assertNotNull(result.getId());
        assertEquals(LeaveStatus.PENDING, result.getStatus());
    }

    @Test
    void create_WhenVacationExceedsRemainingQuota_ReturnsBadRequest() {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Test Employee");
        emp.setAnnualQuota(20);
        employees.save(emp);

        LeaveRequest approvedRequest = new LeaveRequest();
        approvedRequest.setEmployeeId(emp.getId());
        approvedRequest.setType(LeaveType.VACATION);
        approvedRequest.setStartDate(LocalDate.of(2026, 1, 1));
        approvedRequest.setEndDate(LocalDate.of(2026, 1, 17));
        approvedRequest.setDays(17);
        approvedRequest.setStatus(LeaveStatus.APPROVED);
        leaveRequests.save(approvedRequest);

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 4));

        // Act
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> leaveRequestService.create(dto)
        );

        // Assert
        assertEquals("Not enough vacation balance", exception.getMessage());
        assertEquals(before, leaveRequests.count());
    }

    @Test
    void searchByEmployeeName_ReturnsMatchingRequests() {
        //Arrange
        Employee dima = new Employee();
        dima.setName("Dima Smith");
        dima.setAnnualQuota(20);
        employees.save(dima);

        Employee alice = new Employee();
        alice.setName("Alice Brown");
        alice.setAnnualQuota(20);
        employees.save(alice);

        LeaveRequest dimaRequest = new LeaveRequest();
        dimaRequest.setEmployeeId(dima.getId());
        dimaRequest.setType(LeaveType.VACATION);
        dimaRequest.setStartDate(LocalDate.of(2026, 1, 1));
        dimaRequest.setEndDate(LocalDate.of(2026, 1, 2));
        dimaRequest.setDays(2);
        dimaRequest.setStatus(LeaveStatus.PENDING);
        leaveRequests.save(dimaRequest);

        LeaveRequest aliceRequest = new LeaveRequest();
        aliceRequest.setEmployeeId(alice.getId());
        aliceRequest.setType(LeaveType.VACATION);
        aliceRequest.setStartDate(LocalDate.of(2026, 2, 1));
        aliceRequest.setEndDate(LocalDate.of(2026, 2, 2));
        aliceRequest.setDays(2);
        aliceRequest.setStatus(LeaveStatus.PENDING);
        leaveRequests.save(aliceRequest);

        //Act
        List<LeaveRequest> result =
                leaveRequestService.searchByEmployeeName("dima");

        //Assert
        assertEquals(1, result.size());
        assertEquals(dima.getId(), result.get(0).getEmployeeId());
    }

    @Test
    void approve_WhenQuotaAvailable_ApprovesRequest() {
        //Arrange
        Employee employee = new Employee();
        employee.setName("John");
        employee.setAnnualQuota(20);
        employees.save(employee);

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(employee.getId());
        request.setType(LeaveType.VACATION);
        request.setStartDate(LocalDate.of(2026, 3, 1));
        request.setEndDate(LocalDate.of(2026, 3, 5));
        request.setDays(5);
        request.setStatus(LeaveStatus.PENDING);
        leaveRequests.save(request);

        //Act
        LeaveRequest result = leaveRequestService.approve(request.getId());

        //Assert
        assertEquals(LeaveStatus.APPROVED, result.getStatus());
    }

    @Test
    void approve_WhenQuotaNoLongerAvailable_RejectsRequest() {
        //Arrange
        Employee employee = new Employee();
        employee.setName("John");
        employee.setAnnualQuota(20);
        employees.save(employee);

        LeaveRequest approved = new LeaveRequest();
        approved.setEmployeeId(employee.getId());
        approved.setType(LeaveType.VACATION);
        approved.setStartDate(LocalDate.of(2026, 1, 1));
        approved.setEndDate(LocalDate.of(2026, 1, 18));
        approved.setDays(18);
        approved.setStatus(LeaveStatus.APPROVED);
        leaveRequests.save(approved);

        LeaveRequest pending = new LeaveRequest();
        pending.setEmployeeId(employee.getId());
        pending.setType(LeaveType.VACATION);
        pending.setStartDate(LocalDate.of(2026, 3, 1));
        pending.setEndDate(LocalDate.of(2026, 3, 3));
        pending.setDays(3);
        pending.setStatus(LeaveStatus.PENDING);
        leaveRequests.save(pending);

        //Act
        assertThrows(
                IllegalArgumentException.class,
                () -> leaveRequestService.approve(pending.getId())
        );

        LeaveRequest unchanged = leaveRequests.findById(pending.getId()).orElseThrow();
        //Assert
        assertEquals(LeaveStatus.PENDING, unchanged.getStatus());
    }

    @Test
    void approve_ConcurrentRequests_DoesNotExceedQuota() throws Exception {
        Employee employee = new Employee();
        employee.setName("John");
        employee.setAnnualQuota(20);
        employees.save(employee);

        // Already used 18 out of 20 days
        LeaveRequest approved = new LeaveRequest();
        approved.setEmployeeId(employee.getId());
        approved.setType(LeaveType.VACATION);
        approved.setStartDate(LocalDate.of(2026, 1, 1));
        approved.setEndDate(LocalDate.of(2026, 1, 18));
        approved.setDays(18);
        approved.setStatus(LeaveStatus.APPROVED);
        leaveRequests.save(approved);

        // Two pending requests, each would consume the remaining 2 days
        LeaveRequest first = createPendingRequest(employee.getId(), 2);
        LeaveRequest second = createPendingRequest(employee.getId(), 2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> approveFirst = () -> {
            start.await();
            try {
                leaveRequestService.approve(first.getId());
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        };

        Callable<Boolean> approveSecond = () -> {
            start.await();
            try {
                leaveRequestService.approve(second.getId());
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        };

        Future<Boolean> firstResult = executor.submit(approveFirst);
        Future<Boolean> secondResult = executor.submit(approveSecond);

        // Release both threads at approximately the same time
        start.countDown();

        boolean firstApproved = firstResult.get();
        boolean secondApproved = secondResult.get();

        executor.shutdown();

        assertNotEquals(firstApproved, secondApproved);

        int approvedDays = leaveRequests
                .findByEmployeeIdAndTypeAndStatus(
                        employee.getId(),
                        LeaveType.VACATION,
                        LeaveStatus.APPROVED)
                .stream()
                .mapToInt(LeaveRequest::getDays)
                .sum();

        assertEquals(20, approvedDays);
    }

    private LeaveRequest createPendingRequest(Long employeeId, int days) {
        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(employeeId);
        request.setType(LeaveType.VACATION);
        request.setStartDate(LocalDate.of(2026, 3, 1));
        request.setEndDate(LocalDate.of(2026, 3, days));
        request.setDays(days);
        request.setStatus(LeaveStatus.PENDING);

        return leaveRequests.save(request);
    }
}
