<?php // Minimal-Theme: gibt nur den Seiteninhalt aus. ?>
<!doctype html>
<html <?php language_attributes(); ?>>
<head>
<meta charset="<?php bloginfo('charset'); ?>">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title><?php wp_title(''); ?></title>
<?php wp_head(); ?>
</head>
<body <?php body_class(); ?>>
<?php while (have_posts()) { the_post(); the_content(); } ?>
<?php wp_footer(); ?>
</body>
</html>
