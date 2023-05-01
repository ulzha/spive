import { component$ } from '@builder.io/qwik';
import Button from './button';
import styles from './sidebar.module.css';

export default component$(() => (
  <div class={styles.sidebar}>
    <Button>
      <span class="MuiBadge-root-63"><svg class="MuiSvgIcon-root-78 MuiSvgIcon-fontSizeSmall-85" focusable="false" viewBox="0 0 24 24"><path d="M9 2C5.146 2 2 5.146 2 9s3.146 7 7 7a6.959 6.959 0 004.574-1.719l.426.426V16l6 6 2-2-6-6h-1.293l-.426-.426A6.959 6.959 0 0016 9c0-3.854-3.146-7-7-7zm0 2c2.773 0 5 2.227 5 5s-2.227 5-5 5-5-2.227-5-5 2.227-5 5-5z"></path></svg><span class="MuiBadge-badge-64 MuiBadge-anchorOriginTopRightCircle-73 MuiBadge-colorSecondary-66 MuiBadge-invisible-77 MuiBadge-dot-68"></span></span>
    </Button>
    <hr />
    <div class="filler"></div>
    <hr />
    <Button>
      <span class="MuiBadge-root-63"><svg class="MuiSvgIcon-root-78 MuiSvgIcon-fontSizeSmall-85" focusable="false" viewBox="0 0 24 24" style="transform: rotate(90deg);"><path d="M12 6.93L5.93 13l1.5 1.5L12 9.93l4.57 4.57 1.5-1.5L12 6.93z"></path></svg><span class="MuiBadge-badge-64 MuiBadge-anchorOriginTopRightCircle-73 MuiBadge-colorSecondary-66 MuiBadge-invisible-77 MuiBadge-dot-68"></span></span>
    </Button>
  </div>
));
