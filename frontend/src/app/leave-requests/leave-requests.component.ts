import { Component, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { LeaveRequestService } from "../services/leave-request.service";
import {
  Employee,
  LeaveRequest,
  LeaveStatus,
  LeaveType,
} from "../models/leave-request.model";

// NOTE: This component was written quickly for a POC.
// It talks to the API directly, manages state by hand and uses `any` everywhere.
@Component({
  selector: "app-leave-requests",
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: "./leave-requests.component.html",
  styleUrls: ["./leave-requests.component.css"],
})
export class LeaveRequestsComponent implements OnInit {
  requests: LeaveRequest[] = [];
  loading = false;
  employees: Employee[] = [];

  readonly leaveTypes = [
    { value: LeaveType.VACATION, label: "Vacation" },
    { value: LeaveType.SICK, label: "Sick" },
    { value: LeaveType.UNPAID, label: "Unpaid" },
  ];

  submitError = "";
  submitSuccess = "";
  requestForm;
  approvingId: number | null = null;
approveError = '';
approveSuccess = '';

  constructor(
    private leaveRequestService: LeaveRequestService,
    private fb: FormBuilder,
  ) {
    this.requestForm = this.fb.group({
      employeeId: [null as number | null, Validators.required],
      type: [null as LeaveType | null, Validators.required],
      startDate: ["", Validators.required],
      endDate: ["", Validators.required],
    });
  }

  ngOnInit(): void {
    this.load();
    this.loadEmployees();
  }

  load(): void {
    this.loading = true;
    this.leaveRequestService.getAll().subscribe({
      next: (data) => {
        this.requests = data;
        this.loading = false;
      },
      error: (error) => {
        console.error("Failed to load leave requests", error);
        this.loading = false;
      },
    });
  }

  loadEmployees(): void {
    this.leaveRequestService.getEmployees().subscribe({
      next: (employees) => (this.employees = employees),
      error: (error) => console.error("Failed to load employees", error),
    });
  }

  // Wired up by the candidate as part of the assignment.
  approve(id: number): void {
    // TODO (candidate): call POST /api/leave-requests/{id}/approve
    // and handle loading / error / success without a generic alert.
    this.approvingId = id;
  this.approveError = '';
  this.approveSuccess = '';

  this.leaveRequestService.approve(id).subscribe({
    next: updated => {
      this.requests = this.requests.map(request =>
        request.id === updated.id ? updated : request
      );

      this.approvingId = null;
      this.approveSuccess = 'Leave request approved successfully.';
    },
    error: error => {
      this.approvingId = null;
      this.approveError =
        typeof error.error === 'string'
          ? error.error
          : 'Failed to approve leave request.';
    }
  });
  }

  typeLabel(type: number): string {
    if (type == 0) return "Vacation";
    if (type == 1) return "Sick";
    return "Unpaid";
  }

  statusLabel(status: number): string {
    if (status == 0) return "Pending";
    if (status == 1) return "Approved";
    return "Rejected";
  }

  get invalidDateRange(): boolean {
    const start = this.requestForm.value.startDate;
    const end = this.requestForm.value.endDate;

    return !!start && !!end && start > end;
  }

  submit(): void {
    this.submitError = "";
    this.submitSuccess = "";

    if (this.requestForm.invalid || this.invalidDateRange) {
      this.requestForm.markAllAsTouched();
      return;
    }

    const value = this.requestForm.getRawValue();

    this.leaveRequestService
      .create({
        employeeId: value.employeeId!,
        type: value.type!,
        startDate: value.startDate!,
        endDate: value.endDate!,
      })
      .subscribe({
        next: (request) => {
          this.requests = [...this.requests, request];
          this.submitSuccess = "Leave request submitted successfully.";
          this.requestForm.reset();
        },
        error: (error) => {
          this.submitError = error.error || "Failed to submit leave request.";
        },
      });
  }
}
