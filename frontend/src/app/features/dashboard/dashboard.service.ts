import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdminDashboardResponse, MemberDashboardResponse } from './dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  constructor(private readonly http: HttpClient) {}

  get(): Observable<AdminDashboardResponse | MemberDashboardResponse> {
    return this.http.get<AdminDashboardResponse | MemberDashboardResponse>(`${environment.apiUrl}/dashboard`);
  }
}
