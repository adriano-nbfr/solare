import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { DsSplashComponent } from '@dsmpf/ngx-dsmpf/inicializacao/splash';
import { DsAppNavegacao } from '@dsmpf/ngx-dsmpf/navegacao';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    DsSplashComponent
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {

  protected navegacaoInicial = inject(DsAppNavegacao).processandoNavegacaoInicial;

}
