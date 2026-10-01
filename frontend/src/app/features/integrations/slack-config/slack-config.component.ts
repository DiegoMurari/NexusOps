import { Component } from '@angular/core';
import { ConnectorField, ConnectorPanelComponent } from '../connector-panel/connector-panel.component';

@Component({
  selector: 'app-slack-config',
  standalone: true,
  imports: [ConnectorPanelComponent],
  template: `<app-connector-panel type="SLACK" title="Integração com Slack" icon="forum" [fields]="fields" />`
})
export class SlackConfigComponent {
  fields: ConnectorField[] = [
    { key: 'workspace', label: 'Workspace', placeholder: 'empresa' },
    { key: 'channel', label: 'Canal', placeholder: '#suporte' },
  ];
}
