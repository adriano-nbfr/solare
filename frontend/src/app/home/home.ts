import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.html',
  styleUrl: './home.scss'
})
export class Home {
  protected readonly titulo = 'Solare';
  protected readonly descricao =
    'Solicitação e acompanhamento de ambientes e recursos.';
}
