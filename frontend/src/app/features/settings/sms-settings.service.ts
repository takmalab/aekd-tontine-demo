import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SmsSettings, SmsSettingsRequest, SmsTestRequest, SmsTestResponse } from './sms-settings.model';

@Injectable({ providedIn: 'root' })
export class SmsSettingsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/settings/sms`;

  get(): Observable<SmsSettings> {
    return this.http.get<SmsSettings>(this.baseUrl);
  }

  update(request: SmsSettingsRequest): Observable<SmsSettings> {
    return this.http.put<SmsSettings>(this.baseUrl, request);
  }

  sendTest(request: SmsTestRequest): Observable<SmsTestResponse> {
    return this.http.post<SmsTestResponse>(`${this.baseUrl}/test`, request);
  }
}
