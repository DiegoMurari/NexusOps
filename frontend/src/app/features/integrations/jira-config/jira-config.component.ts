import { Component } from '@angular/core';
import { ConnectorField, ConnectorPanelComponent } from '../connector-panel/connector-panel.component';

@Component({
  selector: 'app-jira-config',
  standalone: true,
  imports: [ConnectorPanelComponent],
  template: `<app-connector-panel type="JIRA" title="Integração com Jira" icon="bug_report" [fields]="fields" />`
})
export class JiraConfigComponent {
  fields: ConnectorField[] = [
    { key: 'baseUrl', label: 'URL base', placeholder: 'https://empresa.atlassian.net' },
    { key: 'projectKey', label: 'Chave do projeto', placeholder: 'OPS' },
  ];
}
