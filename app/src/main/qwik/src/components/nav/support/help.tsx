import { component$ } from '@builder.io/qwik';
import { MUIButton, MUIHelpIcon } from '~/integrations/react/mui';
import styles from './support.module.css';

export default component$(() => (
  <div class={styles.support}>
    <MUIButton href="#support">
      <MUIHelpIcon />
      Help
    </MUIButton>
  </div>
));
