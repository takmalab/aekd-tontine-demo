import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FundOverview } from './fund.model';

@Injectable({ providedIn: 'root' })
export class FundOverviewApi {
  private readonly http = inject(HttpClient);

  overview(): Observable<FundOverview> {
    return this.http.get<FundOverview>(`${environment.apiUrl}/funds/overview`);
  }
}
