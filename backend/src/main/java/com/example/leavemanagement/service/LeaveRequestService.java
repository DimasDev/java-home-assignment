package com.example.leavemanagement.service;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LeaveRequestService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveRequestService(EmployeeRepository employeeRepository, LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public List<LeaveRequest> getAll() {
        return leaveRequestRepository.findAll().stream()
                .sorted((a, b) -> b.getStartDate().compareTo(a.getStartDate()))
                .toList();
    }

    public List<LeaveRequest> searchByEmployeeName(@RequestParam String name) {
        return leaveRequestRepository.findByEmployeeNameContainingIgnoreCase(name);
    }

    public LeaveRequest create(@RequestBody CreateLeaveRequestDto dto) {
        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        int days = (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;

        // How many vacation days has the employee already used this year?
        int used = leaveRequestRepository
                .findByEmployeeIdAndTypeAndStatus(dto.getEmployeeId(), LeaveType.VACATION, LeaveStatus.APPROVED)
                .stream()
                .mapToInt(LeaveRequest::getDays)
                .sum();

        // Make sure the request does not exceed the quota.
        if (dto.getType() == LeaveType.VACATION && days > employee.getAnnualQuota() - used) {
            throw new IllegalArgumentException("Not enough vacation balance");
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(dto.getEmployeeId());
        request.setType(dto.getType());
        request.setStartDate(dto.getStartDate());
        request.setEndDate(dto.getEndDate());
        request.setDays(days);
        request.setStatus(LeaveStatus.PENDING);

        return leaveRequestRepository.save(request);
    }

    @Transactional
    public LeaveRequest approve(Long requestId) {
        LeaveRequest request = leaveRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found"));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("Only pending requests can be approved");
        }

        Employee employee = employeeRepository
                .findByIdForUpdate(request.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        if (request.getType() == LeaveType.VACATION) {
            int used = leaveRequestRepository
                    .findByEmployeeIdAndTypeAndStatus(
                            employee.getId(),
                            LeaveType.VACATION,
                            LeaveStatus.APPROVED
                    )
                    .stream()
                    .mapToInt(LeaveRequest::getDays)
                    .sum();

            if (request.getDays() > employee.getAnnualQuota() - used) {
                throw new IllegalArgumentException("Not enough vacation balance");
            }
        }

        request.setStatus(LeaveStatus.APPROVED);

        return leaveRequestRepository.save(request);
    }

}
