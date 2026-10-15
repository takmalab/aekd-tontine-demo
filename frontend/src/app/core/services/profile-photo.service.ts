import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, switchMap, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Taille du carré envoyé au serveur : assez net pour l'écran de profil, léger (~30-60 Ko). */
const PHOTO_SIZE = 320;

/**
 * Photo de profil du compte connecté. L'API exige le JWT : l'image est lue en
 * Blob puis exposée par une URL locale (`photoUrl`), partagée par la barre du
 * haut, le menu et la page « Mon profil ».
 */
@Injectable({ providedIn: 'root' })
export class ProfilePhotoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/profile/photo`;

  private readonly photoUrlSignal = signal<string | null>(null);
  readonly photoUrl = this.photoUrlSignal.asReadonly();

  /** Charge la photo (aucune : null, sans erreur). */
  load(): Observable<unknown> {
    return this.http.get(this.url, { responseType: 'blob' }).pipe(
      tap((blob) => this.setBlob(blob)),
      catchError(() => {
        this.setBlob(null);
        return of(null);
      }),
    );
  }

  /** Recadre l'image au centre, la réduit, puis l'envoie. */
  upload(file: File): Observable<unknown> {
    return new Observable<Blob>((subscriber) => {
      squareJpeg(file, PHOTO_SIZE)
        .then((blob) => {
          subscriber.next(blob);
          subscriber.complete();
        })
        .catch((error) => subscriber.error(error));
    }).pipe(
      switchMap((blob) => {
        const body = new FormData();
        body.append('file', blob, 'photo.jpg');
        return this.http.post(this.url, body).pipe(tap(() => this.setBlob(blob)));
      }),
    );
  }

  remove(): Observable<unknown> {
    return this.http.delete(this.url).pipe(tap(() => this.setBlob(null)));
  }

  /** À la déconnexion : la photo d'un compte ne doit pas rester affichée pour le suivant. */
  reset(): void {
    this.setBlob(null);
  }

  private setBlob(blob: Blob | null): void {
    const previous = this.photoUrlSignal();
    if (previous) {
      URL.revokeObjectURL(previous);
    }
    this.photoUrlSignal.set(blob ? URL.createObjectURL(blob) : null);
  }
}

/** Découpe le plus grand carré centré de l'image et le redimensionne en JPEG. */
function squareJpeg(file: File, size: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const source = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(source);
      const side = Math.min(image.naturalWidth, image.naturalHeight);
      const canvas = document.createElement('canvas');
      canvas.width = size;
      canvas.height = size;
      const context = canvas.getContext('2d');
      if (!context) {
        reject(new Error('canvas'));
        return;
      }
      context.drawImage(
        image,
        (image.naturalWidth - side) / 2,
        (image.naturalHeight - side) / 2,
        side,
        side,
        0,
        0,
        size,
        size,
      );
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('encode'))), 'image/jpeg', 0.85);
    };
    image.onerror = () => {
      URL.revokeObjectURL(source);
      reject(new Error('image'));
    };
    image.src = source;
  });
}
