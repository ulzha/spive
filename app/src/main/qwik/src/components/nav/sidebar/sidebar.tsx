import { component$ } from '@builder.io/qwik';
import { MUIButton, MUIExpandMoreIcon, MUISearchIcon } from '~/integrations/react/mui';
import styles from './sidebar.module.css';

export default component$(() => (
  <div class={styles.sidebar}>
    <MUIButton href="#search">
      <MUISearchIcon />
    </MUIButton>
    <hr />
    <hr />
    <MUIButton href="#expand">
      <MUIExpandMoreIcon style={{ transform: 'rotate(-90deg)' }} />
    </MUIButton>
  </div>
));
